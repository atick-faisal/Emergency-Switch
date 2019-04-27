#define SWITCH_PIN 2

void setup() {
  Serial.begin(9600);
  pinMode(SWITCH_PIN, INPUT_PULLUP);
}

void loop() {
  if(!digitalRead(SWITCH_PIN)) {
    Serial.println('1');
    delay(1000);
  }
}
