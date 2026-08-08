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
                onBack = { nav.popBackStack() }
            )
        }
    }
}
