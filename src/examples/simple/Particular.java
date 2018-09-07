class Particular {
  private int y;
  public Particular(int arg) {
    y = arg;
  }

  public synchronized void incrementY() {
    y++;
  }

  public int getY() {
    return y;
  }
}