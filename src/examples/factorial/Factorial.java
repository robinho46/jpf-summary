import java.math.BigInteger;

public class Factorial {

  public BigInteger fac(BigInteger n) {
    BigInteger result = BigInteger.ONE;

    while (!n.equals(BigInteger.ZERO)) {
      result = result.multiply(n);
      n = n.subtract(BigInteger.ONE);
    }

    return result;
  }

  public static void main(String[] args) {
    Factorial f = new Factorial();
    BigInteger n = BigInteger.valueOf(20);
    f.fac(n);
    f.fac(n);
    f.fac(n);
    f.fac(n);
    f.fac(n);
    System.out.println(f.fac(n));
  }
}