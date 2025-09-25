package com.example.getbusy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.example.getbusy.ui.*
//
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val nav = rememberNavController()
                    val repo = AppGraph.repo

                    NavHost(navController = nav, startDestination = "main") {
                        composable("main") {
                            val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(repo))
                            MainScreen(
                                vm = vm,
                                onOpenManage = { nav.navigate("manage") }
                            )
                        }
                        composable("manage") {
                            val vm: ManageViewModel = viewModel(factory = ManageViewModel.Factory(repo))
                            ManageScreen(
                                vm = vm,
                                onAdd = { nav.navigate("edit/-1") },
                                onEdit = { id -> nav.navigate("edit/$id") },
                                onBack = { nav.popBackStack() }
                            )
                        }

                        composable(
                            route = "edit/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val vm: ManageViewModel = viewModel(factory = ManageViewModel.Factory(repo))
                            val id = backStackEntry.arguments?.getLong("id") ?: -1L
                            if (id > 0) vm.loadForEdit(id)
                            else vm.newForm()
                            EditActivityScreen(
                                vm = vm,
                                onDone = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
