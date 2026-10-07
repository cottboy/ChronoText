package com.chronotext.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chronotext.app.schedule.TaskService
import com.chronotext.app.ui.LogsScreen
import com.chronotext.app.ui.TaskEditScreen
import com.chronotext.app.ui.TaskListScreen
import com.chronotext.app.ui.theme.ChronoTextTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChronoTextTheme {
                AppNav()
            }
        }
        // 打开应用时核对一遍任务闹钟：清理残留、补发被系统漏掉的发送
        lifecycleScope.launch(Dispatchers.IO) {
            TaskService.rescheduleAll(applicationContext)
        }
    }
}

private object Routes {
    const val TASKS = "tasks"
    const val LOGS = "logs"
    // 编辑页路由模板与实际导航地址必须严格一致，避免拼接歧义
    const val EDIT_PATTERN = "edit/{taskId}"
    fun edit(taskId: Long) = "edit/$taskId"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.TASKS || currentRoute == Routes.LOGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.TASKS,
                        onClick = {
                            navController.navigate(Routes.TASKS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == Routes.TASKS) Icons.Filled.Send else Icons.Outlined.Send,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.tab_tasks)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LOGS,
                        onClick = {
                            navController.navigate(Routes.LOGS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (currentRoute == Routes.LOGS) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.tab_logs)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.TASKS) {
                FloatingActionButton(onClick = { navController.navigate(Routes.edit(-1)) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.task_new))
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TASKS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TASKS) {
                TaskListScreen(
                    onEdit = { navController.navigate(Routes.edit(it)) },
                )
            }
            composable(Routes.LOGS) {
                LogsScreen()
            }
            composable(Routes.EDIT_PATTERN) { entry ->
                val taskId = entry.arguments?.getString("taskId")?.toLongOrNull() ?: -1L
                TaskEditScreen(
                    taskId = taskId,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}
