import java.util.LinkedList;
import java.util.List;

public class ReferenceExample {
  private class ExampleObject {
    private int field;
    public ExampleObject() {
      field = 0;
    } 

    public void setField(int value) {
      field = value;
    }

    public int getField() {
      return field;
    }
  }

  private List<ExampleObject> list;
  public ReferenceExample() {
    list = new LinkedList<>();
  }

  public void addToList() {
    list.add(new ExampleObject());
  }

  public void setAllInList(int value) {
    //for(ExampleObject obj : list) {
    for(int i=0; i<list.size();i++) {
      list.get(i).setField(value);
    }
  }

  public void assertCorrectness(int value) {
    //for(ExampleObject obj : list) {
    for(int i=0; i<list.size();i++) {
      assert(list.get(i).getField() == value);
    }
  }


  public static void main(String[] args) {
    ReferenceExample ref = new ReferenceExample();
    ref.addToList();
    ref.assertCorrectness(0);
    ref.setAllInList(42);
    ref.assertCorrectness(42);
    ref.addToList();
    ref.setAllInList(42);
    ref.assertCorrectness(42);
  }
}