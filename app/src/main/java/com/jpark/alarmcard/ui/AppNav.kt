package com.jpark.alarmcard.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm: MainViewModel = hiltViewModel()
            HomeScreen(
                vm = vm,
                onAdd = { nav.navigate("add") }
            )
        }
        composable("add") {
            val vm: MainViewModel = hiltViewModel()
            AddCardScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenBusPicker = { nav.navigate("bus_picker") }
            )
        }
        composable("bus_picker") {
            val vm: MainViewModel = hiltViewModel()
            BusPickerScreen(
                vm = vm,
                onDone = {
                    // add 화면까지 함께 닫고 홈으로
                    nav.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}
