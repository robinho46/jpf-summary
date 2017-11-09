/**
 * Example showing how summaries interact with printing.
 **/

public class PrintingExample {
  private String message;
  public PrintingExample() {
    message = "This is a non-static String";
  }

  public void setMessage(String msg) {
    message = msg;
  }

  public void printMessage() {
    System.out.println(message);
  }

  public static void main(String[] args) {
    PrintingExample printer = new PrintingExample();

    printer.printMessage();

    for(int i=0;i<100;i++) {
      printer.printMessage();
    }
    
  }
}