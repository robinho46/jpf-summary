import java.util.LinkedList;

public class Simple  {
  static int x;

  public static int read() {
    int y = 42;

    if(x==y){
      return 0;
    }

    return 1;
  }

  public static void write(int arg) {
    arg += 5;
    x = arg;
  }

  public static void takeThisObjectAndDoSomething(Particular obj) {
    //do nothing
    obj.incrementY();
  }

  public void doSomethingElse(int arg, boolean flag) {
    // do nothing external, but perform a read/write to local
    flag = !flag;
  }




  public static void main(String[] args) {/*
    new Thread() {
      public void run() {
        new Simple().write(42);
      }
    }.start();*/
    
    new Simple().doSomethingElse(5,true);
    x = 0;
    write(42);
      

    if(x==42){
      System.out.println("Didn't skip method.");
    }else{
      System.out.println("x="+x);
    }

    /*
    for(int i=0; i < 4000; i++) {
      write(42);
    }
    for(int i=0; i < 4000; i++) {
      write(43);
    }*/
    read();

    Particular obj = new Particular(33);
    obj.incrementY();
    takeThisObjectAndDoSomething(obj);
    
    
    //System.out.println(obj.getY());
  }
}