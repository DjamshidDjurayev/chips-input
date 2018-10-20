package co.djuraev.chipsinput.chips;

import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;

public class ChipItem {
  private long id;
  private String title;

  public ChipItem(long id, String title) {
    this.id = id;
    this.title = title;
  }

  ChipItem(String title) {
    this.title = title;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public static ChipItem generateTokenizedEntry(String address) {
    final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(address);
    final String tokenizedAddress = tokens.length > 0 ? tokens[0].getAddress() : address;
    return new ChipItem(tokenizedAddress);
  }

  public static ChipItem generateEntry(String address) {
    return new ChipItem(address);
  }
}
