package ru.seleznev.focusfield.ui.common

import androidx.fragment.app.Fragment
import ru.seleznev.focusfield.AppContainer
import ru.seleznev.focusfield.FocusFieldApp

/** Доступ к DI-контейнеру из фрагментов. */
val Fragment.appContainer: AppContainer
    get() = (requireActivity().application as FocusFieldApp).container
