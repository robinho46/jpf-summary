public class ToString {
  public static void main(String[] args) {
    ToString ex = new ToString();
    // can't be summarised because it allocates a new string
    String s = Integer.toString(0);
    assert(Integer.toString(0).equals("0"));
    System.out.println(Integer.toString(0));
  }
}