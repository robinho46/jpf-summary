import java.util.LinkedList;
import java.util.List;

class ReferenceExample {
  private class ExampleObject {
    private int field;
    ExampleObject() {
      field = 0;
    } 

    void setField(int value) {
      field = value;
    }

    int getField() {
      return field;
    }
  }

  private List<ExampleObject> list;
  private ReferenceExample() {
    list = new LinkedList<>();
  }

  private void addToList() {
    list.add(new ExampleObject());
  }

  private void setAllInList(int value) {
    //for(ExampleObject obj : list) {
    for(int i=0; i<list.size();i++) {
      list.get(i).setField(value);
    }
  }

  private void assertCorrectness(int value) {
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