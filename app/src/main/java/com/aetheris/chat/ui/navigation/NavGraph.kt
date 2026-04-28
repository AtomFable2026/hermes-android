package com.aetheris.chat.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aetheris.chat.ui.screens.chat.ChatScreen
import com.aetheris.chat.ui.screens.conversations.ConversationsScreen
import com.aetheris.chat.ui.screens.settings.SettingsScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat?conversationId={conversationId}"
    const val SETTINGS = "settings"

    fun chatRoute(conversationId: Long? = null): String {
        return if (conversationId != null) {
            "chat?conversationId=$conversationId"
        } else {
            "chat"
        }
    }
}

@Composable
fun AetherisNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATIONS,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onNewChat = {
                    navController.navigate(Routes.chatRoute())
                },
                onOpenChat = { conversationId ->
                    navController.navigate(Routes.chatRoute(conversationId))
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId")
                ?.takeIf { it > 0 }

            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
