package ru.seleznev.focusfield

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.not
import ru.seleznev.focusfield.data.crypto.PasswordHasher
import ru.seleznev.focusfield.data.prefs.SecurePrefs
import java.io.File
import java.io.FileOutputStream

/**
 * Полный флоу регистрации: форма → экран редактирования профиля → сохранение →
 * приложение. Кириллица вводится через Espresso replaceText (adb input text
 * не поддерживает Unicode).
 *
 * Запуск вручную (Gradle-прогон удаляет приложение с данными!):
 * adb install -r app-debug-androidTest.apk
 * adb shell am instrument -e class ru.seleznev.focusfield.RegistrationFlowTest -w \
 *   ru.seleznev.focusfield.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class RegistrationFlowTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var createdEmail: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun startSignedOut() {
        // Общий с приложением репозиторий: снимаем сессию прошлого прогона,
        // чтобы тест стартовал с экрана входа.
        appContainer().authRepository.signOut()
    }

    @After
    fun cleanup() {
        createdEmail?.let { email ->
            val prefs = SecurePrefs.auth(context)
            prefs.edit()
                .remove("user:$email")
                .remove("profile:$email")
                .commit()
            // last_* не удаляется при выходе (так задумано), поэтому после
            // удаления тестового аккаунта перебрасываем его на реально
            // оставшийся пользователь — иначе предзаполнение укажет в пустоту.
            val remaining = prefs.all.keys
                .filter { it.startsWith("user:") }
                .map { it.removePrefix("user:") }
            val last = prefs.getString("last_email", null)
            val editor = prefs.edit()
            if (last !in remaining) {
                if (remaining.isNotEmpty()) {
                    val fallback = remaining.sorted().first()
                    editor.putString("last_email", fallback)
                        .putString(
                            "last_profile",
                            prefs.getString("profile:$fallback", null).orEmpty()
                        )
                } else {
                    editor.remove("last_email").remove("last_profile")
                }
            }
            editor.commit()
        }
        appContainer().authRepository.signOut()
        scenario?.close()
        scenario = null
    }

    @Test
    fun registrationOpensProfileScreen() {
        val email = registerNewUser()

        // Экран профиля открыт сразу после регистрации.
        onView(withId(R.id.profile_title)).check(matches(isDisplayed()))
        onView(withId(R.id.profile_email)).check(matches(withText(email)))
        onView(withId(R.id.input_last_name)).check(matches(withText("Тестов")))

        captureView(R.id.profile_root, "profile_screen.png")

        // «Назад» не возвращает на экран входа, а ведёт вперёд в приложение.
        pressBack()
        onView(withId(R.id.profile_title)).check(doesNotExist())
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
    }

    @Test
    fun avatarOpensSystemPhotoPicker() {
        registerNewUser()
        onView(withId(R.id.profile_title)).check(matches(isDisplayed()))

        // Нажатие на аватар открывает системный Photo Picker (другое окно).
        onView(withId(R.id.avatar_container)).perform(click())

        // Ждём до 8 с, пока фокус уйдёт из приложения: accessibility-дерево
        // чужого окна появляется с задержкой, поэтому опрашиваем и его,
        // и dumpsys window (надёжный источник пакета в фокусе).
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val systemPkgs = listOf("documentsui", "photospicker", "mediaprovider", "gallery")
        var activePkg = ""
        var windowPkgs = emptyList<String>()
        var focusPkg = ""
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            activePkg = ui.rootInActiveWindow?.packageName?.toString().orEmpty()
            windowPkgs = ui.windows?.mapNotNull { w ->
                w.root?.packageName?.toString()?.takeIf { it.isNotBlank() }
            }.orEmpty().distinct()
            focusPkg = focusedWindowPackage()
            val foreign = (activePkg.isNotEmpty() && activePkg != context.packageName) ||
                (focusPkg.isNotEmpty() && focusPkg != context.packageName)
            val picker = (windowPkgs + focusPkg).any { pkg ->
                systemPkgs.any { pkg.contains(it, ignoreCase = true) }
            }
            if (foreign || picker) break
            Thread.sleep(250)
        }
        val hasPicker =
            (activePkg.isNotEmpty() && activePkg != context.packageName) ||
                (focusPkg.isNotEmpty() && focusPkg != context.packageName) ||
                (windowPkgs + focusPkg).any { pkg ->
                    systemPkgs.any { pkg.contains(it, ignoreCase = true) }
                }
        // Скриншот для отчёта (в filesDir).
        val shot = ui.takeScreenshot()
        FileOutputStream(File(context.filesDir, "photo_picker.png")).use { out ->
            shot.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(
            "Photo Picker должен открыться, окна: $windowPkgs, " +
                "активное: $activePkg, фокус: $focusPkg",
            hasPicker
        )

        // Закрываем пикер (чужое окно — системный «назад», не Espresso).
        ui.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        )
        Thread.sleep(1_500)
        onView(withId(R.id.profile_title)).check(matches(isDisplayed()))
    }

    @Test
    fun profileSaveEditsNameAndEntersApp() {
        val email = registerNewUser()
        onView(withId(R.id.profile_title)).check(matches(isDisplayed()))

        // Редактируем имя и сохраняем.
        onView(withId(R.id.input_first_name))
            .perform(replaceText("Тестик"), closeSoftKeyboard())
        onView(withId(R.id.btn_save)).perform(scrollTo(), click())

        // Профиль сохранён — пользователь в приложении.
        onView(withId(R.id.profile_title)).check(doesNotExist())
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))

        // Сохранённое имя дошло до хранилища.
        val stored = SecurePrefs.auth(context).getString("profile:$email", null)
        assertTrue("Имя должно сохраниться: $stored", stored == "Тестов Тестик Тестович")
    }

    @Test
    fun profileSkipEntersAppWithoutSaving() {
        val email = registerNewUser()
        onView(withId(R.id.profile_title)).check(matches(isDisplayed()))

        // «Пропустить» — сразу в приложение, на экран «Фокус».
        onView(withId(R.id.btn_skip)).perform(scrollTo(), click())

        onView(withId(R.id.profile_title)).check(doesNotExist())
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))
        onView(withId(R.id.score_bar)).check(matches(isDisplayed()))

        // Профиль не редактировалось — хранилище содержит ФИО из регистрации,
        // введённое на форме (не изменённое на экране профиля).
        val stored = SecurePrefs.auth(context).getString("profile:$email", null)
        assertTrue("При пропуске имя не изменяется: $stored",
            stored == "Тестов Тест Тестович")
    }

    @Test
    fun accountTabMovesLogoutOutOfSettings() {
        registerNewUser()
        // В приложение (кнопка «Пропустить»).
        onView(withId(R.id.btn_skip)).perform(scrollTo(), click())
        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()))

        // «Аккаунт» — отдельная вкладка нижнего меню с кнопкой выхода.
        onView(withId(R.id.accountFragment)).perform(click())
        onView(withId(R.id.account_email)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_logout)).perform(scrollTo()).check(matches(isDisplayed()))
        captureView(R.id.account_root, "account_screen.png")

        // В настройках раздел «Аккаунт» больше нет.
        onView(withId(R.id.settingsFragment)).perform(click())
        onView(withText("Внешний вид")).check(matches(isDisplayed()))
        onView(withText("Выйти из аккаунта")).check(doesNotExist())
    }

    @Test
    fun accountEditsProfileAndReturns() {
        val email = registerNewUser()
        // В приложение (кнопка «Пропустить»).
        onView(withId(R.id.btn_skip)).perform(scrollTo(), click())

        // «Аккаунт» → «Редактировать профиль».
        onView(withId(R.id.accountFragment)).perform(click())
        onView(withId(R.id.btn_edit_profile)).perform(scrollTo(), click())

        // Режим редактирования: «Пропустить» скрыт, кнопка «Сохранить».
        onView(withId(R.id.profile_title)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_skip)).check(matches(not(isDisplayed())))
        onView(withId(R.id.btn_save)).check(matches(withText("Сохранить")))

        // Правим имя и сохраняем.
        onView(withId(R.id.input_first_name))
            .perform(replaceText("Правлен"), closeSoftKeyboard())
        onView(withId(R.id.btn_save)).perform(scrollTo(), click())

        // Возврат на экран «Аккаунт» (не в приложение).
        onView(withId(R.id.profile_title)).check(doesNotExist())
        onView(withId(R.id.account_name)).check(matches(withText("Тестов Правлен Тестович")))
        onView(withId(R.id.btn_logout)).perform(scrollTo()).check(matches(isDisplayed()))

        // Изменение дошло до хранилища.
        val stored = SecurePrefs.auth(context).getString("profile:$email", null)
        assertTrue("Имя должно сохраниться: $stored",
            stored == "Тестов Правлен Тестович")

        captureView(R.id.account_root, "account_edited.png")
    }

    @Test
    fun accountShowsStatusStatsAndChangesPassword() {
        val email = registerNewUser()
        onView(withId(R.id.btn_skip)).perform(scrollTo(), click())
        onView(withId(R.id.accountFragment)).perform(click())

        // Статус режима и разрешения (верх экрана).
        onView(withId(R.id.account_status)).check(matches(isDisplayed()))
        onView(withId(R.id.account_perm_camera_status))
            .perform(scrollTo()).check(matches(withText("✓ Разрешено")))
        onView(withId(R.id.account_perm_accessibility_status))
            .perform(scrollTo()).check(matches(isDisplayed()))

        // Компактная статистика: у нового аккаунта нули и «—».
        // Данные приходят асинхронно из Room — даём корутине выполниться.
        Thread.sleep(800)
        onView(withId(R.id.account_stat_sessions)).perform(scrollTo())
            .check(matches(withText("0")))
        onView(withId(R.id.account_stat_focus)).perform(scrollTo())
            .check(matches(withText("0 мин")))
        onView(withId(R.id.account_stat_streak)).perform(scrollTo())
            .check(matches(withText("0 дн")))
        onView(withId(R.id.account_stat_best_day)).perform(scrollTo())
            .check(matches(withText("—")))
        captureView(R.id.account_root, "account_full.png")

        // Смена пароля: диалог с тремя полями, успех меняет хеш в хранилище.
        onView(withId(R.id.btn_change_password)).perform(scrollTo(), click())
        onView(withId(R.id.input_current_password))
            .perform(replaceText(PASSWORD), closeSoftKeyboard())
        onView(withId(R.id.input_new_password))
            .perform(replaceText(NEW_PASSWORD), closeSoftKeyboard())
        onView(withId(R.id.input_repeat_password))
            .perform(replaceText(NEW_PASSWORD), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())

        val stored = SecurePrefs.auth(context).getString("user:$email", null)
        assertTrue(
            "Новый пароль должен сохраниться: $stored",
            stored != null && PasswordHasher.verify(NEW_PASSWORD, stored)
        )
        assertTrue(
            "Старый пароль должен перестать подходить",
            stored != null && !PasswordHasher.verify(PASSWORD, stored)
        )
    }

    @Test
    fun accountDeleteNeedsDoubleConfirmAndSignsOut() {
        val email = registerNewUser()
        onView(withId(R.id.btn_skip)).perform(scrollTo(), click())
        onView(withId(R.id.accountFragment)).perform(click())

        // Двойное подтверждение: первый диалог → второй → аккаунт удалён.
        onView(withId(R.id.btn_delete_account)).perform(scrollTo(), click())
        onView(withText("Удалить аккаунт?")).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).perform(click())
        onView(withText("Вы уверены?")).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).perform(click())

        // Удаление асинхронно — ждём перехода на экран входа.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val onAuth = try {
                onView(withId(R.id.input_email)).check(matches(isDisplayed()))
                true
            } catch (_: Throwable) {
                false
            }
            if (onAuth) break
            Thread.sleep(250)
        }
        onView(withId(R.id.input_email)).check(matches(isDisplayed()))

        // Локальные данные аккаунта удалены.
        val prefs = SecurePrefs.auth(context)
        assertTrue("Учётная запись должна быть удалена",
            prefs.getString("user:$email", null) == null)
        assertTrue("Профиль должен быть удалён",
            prefs.getString("profile:$email", null) == null)
    }

    /** Открывает экран входа, переключается в режим регистрации и регистрирует пользователя. */
    private fun registerNewUser(): String {
        val email = "flow-${System.currentTimeMillis()}@example.com"
        createdEmail = email

        scenario = ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.btn_toggle_mode)).perform(click())
        onView(withId(R.id.input_email)).perform(replaceText(email), closeSoftKeyboard())
        onView(withId(R.id.input_password)).perform(replaceText(PASSWORD), closeSoftKeyboard())
        onView(withId(R.id.input_confirm_password))
            .perform(replaceText(PASSWORD), closeSoftKeyboard())
        onView(withId(R.id.input_last_name)).perform(replaceText("Тестов"), closeSoftKeyboard())
        onView(withId(R.id.input_first_name)).perform(replaceText("Тест"), closeSoftKeyboard())
        onView(withId(R.id.input_patronymic))
            .perform(replaceText("Тестович"), closeSoftKeyboard())
        onView(withId(R.id.btn_primary)).perform(scrollTo(), click())

        return email
    }

    /**
     * Пакет активного (в фокусе) окна по dumpsys window: accessibility-дерево
     * не всегда отдаёт чужие окна, а dumpsys — источник истины.
     */
    private fun focusedWindowPackage(): String {
        return try {
            val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("dumpsys window windows")
            val text = pfd.use { fd ->
                java.io.FileInputStream(fd.fileDescriptor)
                    .bufferedReader().use { it.readText() }
            }
            // Вид: mCurrentFocus=Window{abc u0 com.pkg/com.Activity}
            Regex("""mCurrentFocus=Window\{[^}]*?(\S+)/""")
                .find(text)?.groupValues?.get(1).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    /** Снимок экрана профиля в filesDir (для отчёта); рисуется на главном потоке. */
    private fun captureView(viewId: Int, fileName: String) {
        val activeScenario = scenario ?: return
        activeScenario.onActivity { activity ->
            val target = activity.findViewById<View>(viewId) ?: return@onActivity
            val bitmap =
                Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Фон цвета темы: у ScrollView нет своего фона, и без заливки
            // прозрачные места дают «дыры» в PNG для отчёта.
            val tv = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
            canvas.drawColor(tv.data)
            target.draw(canvas)
            android.util.Log.i(
                "FFCapture",
                "target=${target.width}x${target.height} alpha=${target.alpha} " +
                    "visibility=${target.visibility} class=${target.javaClass.simpleName}"
            )
            FileOutputStream(File(context.filesDir, fileName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        // Полный скриншот экрана рядом — для сравнения (не на главном потоке).
        runCatching {
            val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            FileOutputStream(File(context.filesDir, "full_$fileName")).use { out ->
                full.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    private fun appContainer() =
        (context.applicationContext as FocusFieldApp).container

    private companion object {
        const val PASSWORD = "Test123!"
        const val NEW_PASSWORD = "NewPass123!"
    }
}
