// Revisi kode: Fokus pada sinkronisasi akses Firebase dengan semaphore khusus fbdo

#include <Arduino.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <Adafruit_Sensor.h>
#include <DHT.h>
#include <WiFiManager.h>

// Firebase credentials
#define FIREBASE_API_KEY "your key"
#define FIREBASE_URL "your url/"
#define USER_EMAIL "email"
#define USER_PASSWORD "pass"

// Pin assignments
#define RELAY_PIN 27
#define TOUCH_PIN 4
#define TOUCH_THRESHOLD 40
#define WATER_SENSOR_PIN 32
#define DHT_PIN 16
#define DHT_TYPE DHT22
#define POWER_WATER_LEVEL 12

// Firebase setup
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

DHT dht(DHT_PIN, DHT_TYPE);

bool relayState = false;
int lastTouchState = 0;
bool otomatis = false;
float humidity = 0, temperature = 0;
int waterLevel = 0, normalize_water_level = 0;
unsigned long lastTouchTime = 0;
const unsigned long debounceDelay = 100;

// Semaphores
SemaphoreHandle_t xSemaphore;
SemaphoreHandle_t xSemaphoreFbdo;

// Task handles
TaskHandle_t TaskSensor, TaskTouch, TaskControl, TaskFirebase, TaskReset;

void connectWiFiAndFirebase() {
  WiFiManager wm;
  bool res = wm.autoConnect("Smart Dehumidifier Setup");

  if (!res) {
    Serial.println("Gagal konek WiFi! Restart...");
    delay(3000);
    ESP.restart();
  }

    while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    }

  Serial.println("WiFi terhubung.");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());

  //  Tunggu kestabilan koneksi
  delay(3000); // Delay tambahan untuk memastikan koneksi stabil

  //  Validasi DNS resolusi Firebase
  IPAddress firebaseIP;
  if (!WiFi.hostByName("smart-portable-dehumidifier-default-rtdb.asia-southeast1.firebasedatabase.app", firebaseIP)) {
    Serial.println("Gagal resolve DNS Firebase, restart...");
    delay(3000);
    ESP.restart();
  }

  //  Inisialisasi Firebase
  config.api_key = FIREBASE_API_KEY;
  config.database_url = FIREBASE_URL;
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  //  Tunggu hingga siap
  int retries = 0;
  while (!Firebase.ready() && retries < 10) {
    Serial.println("Menunggu Firebase...");
    delay(1000);
    retries++;
  }

  if (!Firebase.ready()) {
    Serial.println("Gagal konek Firebase setelah 10 detik, restart...");
    delay(3000);
    ESP.restart();
  }

  Serial.println("Firebase Connected!");
}


void setup() {
  Serial.begin(115200);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(POWER_WATER_LEVEL, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);
  digitalWrite(POWER_WATER_LEVEL, HIGH);
  dht.begin();

  connectWiFiAndFirebase();
  xSemaphore = xSemaphoreCreateMutex();
  xSemaphoreFbdo = xSemaphoreCreateMutex();

  xTaskCreatePinnedToCore(sensorTask, "SensorTask", 10240, NULL, 1, &TaskSensor, 1);
  xTaskCreatePinnedToCore(touchTask, "TouchTask", 10240, NULL, 2, &TaskTouch, 1);
  xTaskCreatePinnedToCore(controlTask, "ControlTask", 10240, NULL, 1, &TaskControl, 1);
  xTaskCreatePinnedToCore(firebaseTask, "FirebaseTask", 10240, NULL, 2, &TaskFirebase, 1);
  xTaskCreatePinnedToCore(resetWifiTask, "Reset Wifi", 10240, NULL, 1, &TaskReset, 1);
}

void loop() {}

void resetWifiTask(void *pvParameters) {
  WiFiManager wm;
  while (1) {
    if (Firebase.ready() && xSemaphoreTake(xSemaphoreFbdo, portMAX_DELAY)) {
      if (Firebase.RTDB.getBool(&fbdo, "/resetWifi") && fbdo.boolData()) {
        Firebase.RTDB.setBool(&fbdo, "/resetWifi", false);
        xSemaphoreGive(xSemaphoreFbdo);
        delay(3000);
        wm.resetSettings();
        ESP.restart();
      } else {
        xSemaphoreGive(xSemaphoreFbdo);
      }
    }
    vTaskDelay(2300 / portTICK_PERIOD_MS);
  }
}

void sensorTask(void *pvParameters) {
  while (1) {
    if (Firebase.ready()) {
      float hum = dht.readHumidity();
      float temp = dht.readTemperature();
      int water = analogRead(WATER_SENSOR_PIN);
      int normWater = (water / 1170.0) * 100;

      if (xSemaphoreTake(xSemaphore, portMAX_DELAY)) {
        humidity = hum;
        temperature = temp;
        waterLevel = water;
        normalize_water_level = normWater;
        xSemaphoreGive(xSemaphore);
      }

      if (!isnan(hum) && !isnan(temp)) {
        Serial.printf("Sensor -> H: %.2f%%, T: %.2f°C, WL: %d (%d%%)\n", hum, temp, water, normWater);
      } else {
        Serial.println("DHT error");
      }
    }
    vTaskDelay(2500 / portTICK_PERIOD_MS);
  }
}

void touchTask(void *pvParameters) {
  while (1) {
    int touchValue = touchRead(TOUCH_PIN);
    if (touchValue < TOUCH_THRESHOLD && millis() - lastTouchTime > debounceDelay) {
      lastTouchTime = millis();
      lastTouchState = (lastTouchState + 1) % 3;
      otomatis = (lastTouchState == 2);

      if (lastTouchState == 0) relayState = false;
      else if (lastTouchState == 1) relayState = true;

      digitalWrite(RELAY_PIN, relayState ? HIGH : LOW);
      Serial.printf("Mode: %s\n", otomatis ? "Auto" : relayState ? "Manual ON" : "OFF");

      if (xSemaphoreTake(xSemaphoreFbdo, portMAX_DELAY)) {
        Firebase.RTDB.setInt(&fbdo, "/mode", lastTouchState);
        xSemaphoreGive(xSemaphoreFbdo);
      }
    }
    vTaskDelay(700 / portTICK_PERIOD_MS);
  }
}

void controlTask(void *pvParameters) {
  while (1) {
    if (Firebase.ready() && xSemaphoreTake(xSemaphoreFbdo, portMAX_DELAY)) {
      if (Firebase.RTDB.getInt(&fbdo, "/mode")) {
        int modeValue = fbdo.intData();
        if (modeValue >= 0 && modeValue <= 2) {
          lastTouchState = modeValue;
          otomatis = (modeValue == 2);
          relayState = (modeValue == 1);
          digitalWrite(RELAY_PIN, relayState ? HIGH : LOW);
        }
      }
      xSemaphoreGive(xSemaphoreFbdo);
    }

    if (xSemaphoreTake(xSemaphore, portMAX_DELAY)) {
      if (normalize_water_level > 90) {
        relayState = false;
        digitalWrite(RELAY_PIN, LOW);
        if (xSemaphoreTake(xSemaphoreFbdo, portMAX_DELAY)) {
          Firebase.RTDB.setInt(&fbdo, "/mode", 0);
          xSemaphoreGive(xSemaphoreFbdo);
        }
        Serial.println("Water level tinggi, Relay OFF");
      }
      if (otomatis) {
        relayState = (humidity > 60.0);
        digitalWrite(RELAY_PIN, relayState ? HIGH : LOW);
        Serial.printf("Auto Mode: Humidity %.2f%% -> Relay %s\n", humidity, relayState ? "ON" : "OFF");
      }
      xSemaphoreGive(xSemaphore);
    }
    vTaskDelay(2000 / portTICK_PERIOD_MS);
  }
}

void firebaseTask(void *pvParameters) {
  while (1) {
    if (Firebase.ready() && xSemaphoreTake(xSemaphore, portMAX_DELAY)) {
      float hum = humidity;
      float temp = temperature;
      int wl = normalize_water_level;
      xSemaphoreGive(xSemaphore);

      if (xSemaphoreTake(xSemaphoreFbdo, portMAX_DELAY)) {
        Firebase.RTDB.setFloat(&fbdo, "/humidity", hum);
        Firebase.RTDB.setFloat(&fbdo, "/temperature", temp);
        Firebase.RTDB.setInt(&fbdo, "/waterLevel", wl);
        xSemaphoreGive(xSemaphoreFbdo);
      }
    }
    vTaskDelay(3500 / portTICK_PERIOD_MS);
  }
}

