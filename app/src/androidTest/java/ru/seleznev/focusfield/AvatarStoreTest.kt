package ru.seleznev.focusfield

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.seleznev.focusfield.data.prefs.SecurePrefs
import ru.seleznev.focusfield.data.repo.AuthRepository
import java.io.File
import java.io.FileOutputStream

/**
 * Инструментальная проверка аватара профиля: сохранение выбранного фото
 * (как из Photo Picker — content:// или file://), даунсемплинг до 512 px,
 * чтение bitmap. Реальные файлы в filesDir.
 */
@RunWith(AndroidJUnit4::class)
class AvatarStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repo: AuthRepository
    private lateinit var email: String

    @Before
    fun setUp() = runBlocking {
        repo = AuthRepository(context)
        email = "avatar-${System.currentTimeMillis()}@example.com"
        val registered = repo.register(email, "Test123!", "Тестов Тест")
        check(registered.isSuccess) { registered.exceptionOrNull()?.message.orEmpty() }
    }

    @After
    fun tearDown() {
        // Уборка: файл аватара и учётная запись тестового пользователя.
        repo.avatarFile(email)?.delete()
        SecurePrefs.auth(context).edit()
            .remove("user:$email")
            .remove("profile:$email")
            .remove("session_email")
            .commit()
        repo.signOut()
    }

    @Test
    fun avatarIsSavedScaledAndReadable() = runBlocking {
        // Большое «фото» 1600x1200 — как из камеры/галереи.
        val source = File(context.cacheDir, "avatar_source.jpg")
        val big = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use { out ->
            big.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        big.recycle()

        val saved = repo.saveAvatar(Uri.fromFile(source))
        source.delete()
        assertTrue("Сохранение должно пройти: ${saved.exceptionOrNull()?.message}",
            saved.isSuccess)

        val file = repo.avatarFile()
        assertNotNull("Файл аватара должен существовать", file)
        assertTrue(file!!.exists() && file.length() > 0)

        val loaded = repo.avatarBitmap()
        assertNotNull("Аватар должен читаться в bitmap", loaded)

        // Даунсемплинг: длинная сторона не больше MAX_AVATAR_PX (512).
        val maxSide = maxOf(loaded!!.width, loaded.height)
        assertTrue("Длинная сторона $maxSide должна быть <= 512", maxSide <= 512)

        // Пропорции сохранены (1600x1200 → 4:3).
        val ratio = loaded.width.toFloat() / loaded.height
        assertTrue("Соотношение сторон должно остаться 4:3, получено $ratio",
            Math.abs(ratio - 4f / 3f) < 0.05f)
    }

    @Test
    fun avatarWithoutPhotoIsNull() {
        // Фото ещё не выбиралось — файла нет, bitmap пустой.
        val file = repo.avatarFile()
        assertTrue(file == null || !file.exists())
        assertEquals(null, repo.avatarBitmap())
    }

    @Test
    fun avatarFileIsNamedByAccount() {
        val file = repo.avatarFile(email)
        assertNotNull(file)
        assertTrue("Имя файла содержит email: ${file!!.name}",
            file.name.startsWith("avatar_") && file.name.endsWith(".jpg"))
        // Небезопасные символы email не попадают в имя файла.
        assertTrue("Лишние символы вычищены: ${file.name}",
            !file.name.contains('/') && !file.name.contains('\\'))
        // Файл не пересекается с аватаром другого аккаунта.
        val other = repo.avatarFile("other@example.com")
        assertTrue("Файлы разных аккаунтов различаются", file.name != other?.name)
    }

    @Test
    fun decodeScaledRejectsGarbage() {
        // Повреждённые данные не роняют приложение.
        val garbage = File(context.cacheDir, "garbage.jpg")
        garbage.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val saved = runBlocking { repo.saveAvatar(Uri.fromFile(garbage)) }
        garbage.delete()
        assertTrue("Мусорные данные должны дать ошибку, а не крэш", saved.isFailure)
    }

    @Test
    fun bitmapFactoryDecodesJpeg() {
        // Контроль: тестовые данные вообще декодируются (самопроверка окружения).
        val file = File(context.cacheDir, "selfcheck.jpg")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bmp.recycle()
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        file.delete()
        assertNotNull(decoded)
        assertEquals(64, decoded!!.width)
    }
}
