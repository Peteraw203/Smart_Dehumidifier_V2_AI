package com.example.smartdehumidifier
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import  com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth
import androidx.navigation.compose.*
//import  androidx.compose.ui.platform.LocalContext
//import android.speech.SpeechRecognizer
//import android.speech.RecognizerIntent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.activity.compose.rememberLauncherForActivityResult
import  androidx.compose.runtime.Composable
//import  androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
//import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.*
import kotlinx.coroutines.*
import com.google.firebase.*
import com.google.firebase.ai.type.GenerativeBackend



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        setContent {
            val navController = rememberNavController()
            var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

            NavHost(
                navController = navController,
                startDestination = if (isLoggedIn) "dashboard" else "login"
            ) {
                composable("login") {
                    LoginScreen(auth = auth, onLoginSuccess = {
                        isLoggedIn = true
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    })
                }
                composable("dashboard") {

                    IoTDashboard(
                        onLogout = {
                            auth.signOut()
                            isLoggedIn = false
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        },
                        onOpenSettings = {
                            navController.navigate("settings")
                        },
                        onOpenAiPrompt = {
                            navController.navigate("aiPrompt")
                        }
                    )
                }
                composable("settings") {
                    SettingsPage(
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            auth.signOut()
                            isLoggedIn = false
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    )
                }

                composable("aiPrompt") {
                    AiPromptScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
//@Composable
//fun RequestAudioPermission() {
//    val context = LocalContext.current
//    val permissionLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.RequestPermission(),
//        onResult = { isGranted ->
//            if (!isGranted) {
//                Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
//            }
//        }
//    )
//
//    LaunchedEffect(Unit) {
//        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
//        }
//    }
//}


@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, auth: FirebaseAuth) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login to Dehumidifier", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.Cyan,
                unfocusedLabelColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.Cyan,
                unfocusedLabelColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    loginStatus = "Email and password must not be empty"
                } else {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                             if (task.isSuccessful) {
                                 loginStatus = "Login successful!"
                                 onLoginSuccess()
                            } else {
                                "Login failed: ${task.exception?.localizedMessage}"
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
        ) {
            Text("Sign In", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(loginStatus, color = if (loginStatus.contains("successful")) Color.Green else Color.Red)

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    loginStatus = "Email and password must not be empty"
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                loginStatus = "Registration successful!"
                                onLoginSuccess()
                            } else {
                                "Registration failed: ${task.exception?.localizedMessage}"
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)) // Green color
        ) {
            Text("Register Account", color = Color.White, fontWeight = FontWeight.Bold)
        }

    }
}

@Composable
fun IoTDashboard(onLogout: () -> Unit, onOpenSettings: () -> Unit, onOpenAiPrompt: () ->Unit) {
    val auth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance("https://smart-portable-dehumidifier-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val humidityRef = database.getReference("humidity")
    val modeRef = database.getReference("mode")
    val temperatureRef = database.getReference("temperature")
    val waterLevelRef = database.getReference("waterLevel")

    var humidity by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }
    var temperature by remember { mutableIntStateOf(0) }
    var waterLevel by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        humidityRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                humidity = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        temperatureRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                temperature = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        waterLevelRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                waterLevel = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        modeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mode = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        modeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mode = snapshot.getValue(Int::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        })

    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(45.dp))
            Text(
                "SMART DEHUMIDIFIER",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(80.dp))

            // Circular Gauge
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawCircle(color = Color.DarkGray, style = Stroke(width = 80f))
                    drawArc(
                        color = Color.Cyan,
                        startAngle = -90f,
                        sweepAngle = (humidity / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = 80f, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$humidity%",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusCard("Temperature", "$temperature°C", Color.DarkGray, Color(0xFFE3F2FD))
                StatusCard(
                    "Water Level", "$waterLevel%",
                    Color.DarkGray,
                    when {
                        waterLevel <= 35 -> Color.Green
                        waterLevel <= 70 -> Color.Yellow
                        else -> Color.Red
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val newMode = (mode + 1) % 3
                    modeRef.setValue(newMode)
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    when (mode) {
                        1 -> Color.Green
                        2 -> Color.Blue
                        else -> Color.Gray
                    }
                )
            ) {
                Text(
                    text = when (mode) {
                        1 -> "On"
                        2 -> "Auto Mode"
                        else -> "Off"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onOpenAiPrompt() },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)) // Ungu
            ) {
                Text("Dehumidifier AI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

        }
    }

        Spacer(modifier = Modifier.height(20.dp))

        //Tombol Settings
        Button(
            onClick = { onOpenSettings() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text("⚙️", color = Color.White, fontSize = 15.sp)
        }

}
@Composable
fun AiPromptScreen(onBack: () -> Unit) {
    var prompt by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Dehumidifier AI Prompt",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Masukkan pertanyaan...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Cyan,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.Cyan,
                unfocusedLabelColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                response = "Menunggu jawaban Dehumidifier AI..."

                // Jalankan di Coroutine Scope
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val model = Firebase.ai(
                            backend = GenerativeBackend.googleAI()
                        ).generativeModel("gemini-2.0-flash")

                        val result = model.generateContent(prompt)
                        withContext(Dispatchers.Main) {
                            response = result.text ?: "Tidak ada jawaban."
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            response = "Gagal mengambil jawaban AI: ${e.message}"
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            enabled = !isLoading
        ) {
            Text("Kirim ke AI", color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Jawaban AI:",
            color = Color.LightGray,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = response,
            color = Color.White,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Kembali", color = Color.White)
        }
    }
}



// Status Card (Temperature & Water Level) dengan border ganda
@Composable
fun StatusCard(label: String, value: String, borderColor: Color, outerBorderColor: Color) {
    Box(
        modifier = Modifier
            .border(width = 6.dp, color = outerBorderColor, shape = RoundedCornerShape(12.dp)) // Border luar
            .border(width = 3.dp, color = borderColor, shape = RoundedCornerShape(12.dp)) // Border dalam
    ) {
        Card(
            modifier = Modifier
                .width(150.dp)  // Ukuran tetap
                .height(100.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black) // Warna latar belakang
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = label, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = value, fontSize = 25.sp, color = Color.White)
            }
        }
    }
}
