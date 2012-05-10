#include "SoftModem.h"

SoftModem modem;
int i;
void setup()
{
  Serial.begin(9600);
  delay(1000);
  modem.begin();
  i=0;
}
void writes(char* s){
   int x = strlen(s); 
   for(i=0;i<x;i++){
     modem.write(s[i]);
     delayMicroseconds(1);
   }
}
void loop()
{
     int sensorValue = analogRead(A8);
     
     Serial.print(sensorValue,DEC); 
     Serial.write("\n");
    //while(1) { 
        char buffer[100];
        //uint8_t m1 = sensorValue&255;
        //uint8_t m2 = sensorValue>>8;
        sprintf(buffer,"%d ",sensorValue);
        writes(buffer);
        //modem.write('h');
        //modem.write(m2);
        
        
    // }
  
}
