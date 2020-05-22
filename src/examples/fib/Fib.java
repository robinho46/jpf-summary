public class Fib {

  public int fib(int n) {
    if(n == 0 || n == 1) return n;

    return fib(n-2) + fib(n-1);
  }

  public static void main(String[] args) {
    Fib f = new Fib();

    f.fib(15);
  }
}