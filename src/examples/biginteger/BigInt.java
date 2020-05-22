import java.math.BigInteger;
public class BigInt  {

  public static void main(String[] args) {
    System.out.println("testing arithmetic operations of BigInteger objects");

    BigInteger big = new BigInteger("4200000000000000000");
    BigInteger o = new BigInteger("100000000000000");
    BigInteger notSoBig = new BigInteger("1");

    BigInteger x = big.add(notSoBig);
    String s = x.toString();
    System.out.println("x = " + s);
    assert s.equals("4200000000000000001");

    x = big.divide(o);
    int i = x.intValue();
    assert i == 42000;
  }
}

