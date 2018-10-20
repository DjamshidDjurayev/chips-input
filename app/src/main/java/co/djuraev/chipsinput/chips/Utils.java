package co.djuraev.chipsinput.chips;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import java.util.regex.Pattern;

public class Utils {
  /**
   * This pattern comes from android.util.Patterns. It has been tweaked to handle a "1" before parens, so numbers such
   * as "1 (425) 222-2342" match.
   */
  private static final Pattern PHONE_PATTERN = Pattern.compile(
      "(\\+[0-9]+[\\- \\.]*)?"
          + "(1?[ ]*\\([0-9]+\\)[\\- \\.]*)?"
          + "([0-9][0-9\\- \\.][0-9\\- \\.]+[0-9])");

  public static boolean phoneNumberMatch(String number) {
    // PhoneNumberUtil). One complication is that it requires the sender's region which
    // comes from the CurrentCountryIso. For now, let's just do this simple match.
    return !TextUtils.isEmpty(number) && PHONE_PATTERN.matcher(number).matches();
  }

  public static void copyToClipboard(Context context, String copyText) {
    if (copyText == null || TextUtils.isEmpty(copyText)) return;

    ClipboardManager clipboard =
        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText("OTP", copyText);
    if (clipboard != null) {
      clipboard.setPrimaryClip(clip);
    }
  }

  public static ClipData getTextFromClipboard(Context context) {
    final ClipboardManager clipboard =
        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    return clipboard.getPrimaryClip();
  }
}
