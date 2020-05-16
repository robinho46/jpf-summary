package exception;

public class ExceptionExample {
  private int x;
  private Exception except;
  public ExceptionExample() {
    x = 0;
  }

  public void throwingMethod() throws Exception {
    if(x==0) throw except;
  }

  public static void main(String[] args) {
    ExceptionExample ex = new ExceptionExample();
    ex.except = new Exception("An exception occurred.");

    try {
      ex.throwingMethod();
      assert(false);
    }catch(Exception e) {
      System.out.println(e);
    }

    try {
      ex.throwingMethod();
      assert(false);
    }catch(Exception e) {
      System.out.println(e);
    }


  }
}