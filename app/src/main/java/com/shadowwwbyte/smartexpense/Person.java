package com.shadowwwbyte.smartexpense;
public class Person {
  private static int COUNTER = 0;
  private final int id;
  private String name;
  public Person(String name){ this.id = ++COUNTER; this.name = name; }
  public int getId(){ return id; }
  public String getName(){ return name; }
  public void setName(String n){ this.name = n; }
  @Override public String toString(){ return name; }
}
