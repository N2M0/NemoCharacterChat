package com.squaredream.nemocharacterchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.squaredream.nemocharacterchat.ui.screens.ApiKeyScreen
import com.squaredream.nemocharacterchat.ui.screens.ChatListScreen
import com.squaredream.nemocharacterchat.ui.screens.ChatScreen
import com.squaredream.nemocharacterchat.ui.screens.MainScreen
import com.squaredream.nemocharacterchat.ui.theme.NemoCharacterChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NemoCharacterChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main_screen"
                    ) {
                        composable("main_screen") {
                            MainScreen(navController = navController)
                        }
                        composable("chat_list_screen") {
                            ChatListScreen(navController = navController)
                        }
                        composable("chat_screen/{characterId}") { backStackEntry ->
                            val characterId = backStackEntry.arguments?.getString("characterId") ?: ""
                            ChatScreen(navController = navController, characterId = characterId)
                        }
                        composable("api_key_screen") {
                            ApiKeyScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}