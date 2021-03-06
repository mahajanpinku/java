package com.bytegeeks.protobuf;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

import example.enumerations.EnumExample;
import example.enumerations.EnumExample.DayOfTheWeek;
import example.enumerations.EnumExample.EnumMessage;


public class EnumMain {
  
  public EnumMessage init() {

    // Use a builder - EnumMessage builder. Autogenerated code from proto file has this
    EnumMessage.Builder builder = EnumMessage.newBuilder();

    // Use integer to set ENUM, 4 = WEDNESDAY
    builder.setDayOfTheWeekValue(4);
    System.out.println("Set via integer:" + builder.toString());

    // Set using ENUM itself
    builder.setDayOfTheWeek(DayOfTheWeek.FRIDAY);
    System.out.println("Set via ENUM: " + builder.toString());
    
    // Generate the message from builder
    EnumMessage message = builder.build();
    // Try to write the message in field, just to see what is it
    // Open the file too.. it will be binary
    FileOutputStream fos;
    try {
      fos = new FileOutputStream("enum_message.bin");
      message.writeTo(fos);
      fos.close();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    // Send as byte array
    byte bytes[] = message.toByteArray();

    // Read back from file..
    // Multiple ways of reading the message.. try with SimpleMessage.
    FileInputStream fis;
    try {
      fis = new FileInputStream("enum_message.bin");
      EnumMessage msgFromFile = EnumMessage.parseFrom(fis);
      System.out.println("Message after reading from file: " + msgFromFile);
      fis.close();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return message;
  }
  public static void main(String[] args) {
    EnumMain main = new EnumMain();
    main.init();
  }
}
