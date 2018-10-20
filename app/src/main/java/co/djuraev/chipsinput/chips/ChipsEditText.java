/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.djuraev.chipsinput.chips;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.QwertyKeyListener;
import android.text.style.ImageSpan;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import co.djuraev.chipsinput.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * RecipientEditTextView is an auto complete text view for use with applications that use the new Chips UI for
 * addressing a message to recipients.
 */
public class ChipsEditText extends AppCompatMultiAutoCompleteTextView
    implements Callback, GestureDetector.OnGestureListener, OnDismissListener,
    TextView.OnEditorActionListener {

  private static final int SCROLLING_BOUNDS_THRESHOLD_IN_PIXELS = 2;
  private static final char COMMIT_CHAR_COMMA = ',';
  private static final char COMMIT_CHAR_SEMICOLON = ';';
  private static final char COMMIT_CHAR_SPACE = ' ';
  private static final String SEPARATOR =
      String.valueOf(COMMIT_CHAR_COMMA) + String.valueOf(COMMIT_CHAR_SPACE);
  private static final String TAG = ChipsEditText.class.getSimpleName();
  private static final int DISMISS = "dismiss".hashCode();
  static final int CHIP_LIMIT = 2;
  private static final int MAX_CHIPS_PARSED = 50;
  private static int sSelectedTextColor = -1;
  private Drawable mChipBackground = null;
  private Drawable mChipDelete = null;
  private Drawable mChipIcon = null;
  private Drawable mInvalidChipBackground;
  private Drawable mChipBackgroundPressed;

  private float mChipHeight;
  private float mChipFontSize;
  private float mLineSpacingExtra;
  private int mChipPadding;
  private int mChipIconBackgroundColor;

  private Tokenizer mTokenizer;
  private Validator mValidator;
  private DrawableRecipientChip mSelectedChip;
  private Bitmap mDefaultContactPhoto;
  private ImageSpan mMoreChip;
  private final ArrayList<String> mPendingChips = new ArrayList<>();
  private final Handler mHandler;
  private int mPendingChipsCount = 0;
  private boolean mNoChips = false;
  private ArrayList<DrawableRecipientChip> mTemporaryRecipients;
  private ArrayList<DrawableRecipientChip> mRemovedSpans;
  private boolean mShouldShrink = true;
  private GestureDetector mGestureDetector;
  private String mCopyAddress;
  private TextWatcher mTextWatcher;

  private ScrollView mScrollView;
  private boolean mTriedGettingScrollView;
  private static int sExcessTopPadding = -1;
  private int mActionBarHeight;
  private boolean mAttachedToWindow;
  private final Runnable mAddTextWatcher;
  private final Runnable mHandlePendingChips;
  private IChipListener mChipListener;
  private int mPreviousChipsCount = 0;
  private final EnumSet<FocusBehavior> mFocusBehavior = EnumSet.allOf(FocusBehavior.class);
  private int mStartTouchY = -1;
  private boolean mIsScrolling = false;

  public enum FocusBehavior {
    SHRINK_WHEN_LOST_FOCUS, EXPAND_WHEN_GOT_FOCUS
  }

  public interface IChipListener {
    void onDataChanged();
  }

  public ChipsEditText(final Context context, final AttributeSet attrs) {
    super(context, attrs);
    mAddTextWatcher = new Runnable() {
      @Override public void run() {
        if (mTextWatcher == null) {
          mTextWatcher = new RecipientTextWatcher();
          addTextChangedListener(mTextWatcher);
        }
      }
    };
    mHandlePendingChips = new Runnable() {
      @Override public void run() {
        handlePendingChips();
      }
    };
    setChipDimensions(context, attrs);
    if (sSelectedTextColor == -1) {
      sSelectedTextColor = context.getResources().getColor(android.R.color.white);
    }
    setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    setOnItemClickListener(null);
    setCustomSelectionActionModeCallback(this);
    mHandler = new Handler() {
      @Override public void handleMessage(final Message msg) {
        if (msg.what == DISMISS) {
          ((ListPopupWindow) msg.obj).dismiss();
          return;
        }
        super.handleMessage(msg);
      }
    };
    mTextWatcher = new RecipientTextWatcher();
    addTextChangedListener(mTextWatcher);
    mGestureDetector = new GestureDetector(context, this);
    setOnEditorActionListener(this);
    setTokenizer(null);
  }

  /**
   * When an item in the suggestions list has been clicked, create a chip from the contact information of
   * the selected item.
   */
  @Override public void setOnItemClickListener(final OnItemClickListener listener) {
    super.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(final AdapterView<?> parent, final View view, final int position,
          final long id) {

        if (position < 0) {
          return;
        }
        submitItemAtPosition(position);
        if (listener != null) {
          listener.onItemClick(parent, view, position, id);
        }
      }
    });
  }

  @Override protected void onDetachedFromWindow() {
    mAttachedToWindow = false;
    super.onDetachedFromWindow();
  }

  @Override protected void onAttachedToWindow() {
    mAttachedToWindow = true;
    super.onAttachedToWindow();
  }

  @Override
  public boolean onEditorAction(final TextView view, final int action, final KeyEvent keyEvent) {
    if (action == EditorInfo.IME_ACTION_DONE) {
      if (commitDefault()) {
        return true;
      }
      if (mSelectedChip != null) {
        clearSelectedChip();
        return true;
      } else if (focusNext()) {
        return true;
      }
    }
    return false;
  }

  @Override public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
    final InputConnection connection = super.onCreateInputConnection(outAttrs);
    final int imeActions = outAttrs.imeOptions & EditorInfo.IME_MASK_ACTION;
    if ((imeActions & EditorInfo.IME_ACTION_DONE) != 0) {
      // clear the existing action
      outAttrs.imeOptions ^= imeActions;
      // set the DONE action
      outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;
    }
    if ((outAttrs.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
      outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
    }
    outAttrs.actionId = EditorInfo.IME_ACTION_DONE;
    outAttrs.actionLabel = getContext().getString(R.string.done);
    return connection;
  }

  DrawableRecipientChip getLastChip() {
    DrawableRecipientChip last = null;
    final DrawableRecipientChip[] chips = getSortedRecipients();
    if (chips != null && chips.length > 0) {
      last = chips[chips.length - 1];
    }
    return last;
  }

  @Override public void onSelectionChanged(final int start, final int end) {
    // When selection changes, see if it is inside the chips area.
    // If so, move the cursor back after the chips again.
    final DrawableRecipientChip last = getLastChip();
    if (last != null && start < getSpannable().getSpanEnd(last)) {
      // Grab the last chip and set the cursor to after it.
      setSelection(Math.min(getSpannable().getSpanEnd(last) + 1, getText().length()));
    }
    super.onSelectionChanged(start, end);
  }

  @Override public void onRestoreInstanceState(final Parcelable state) {
    if (!TextUtils.isEmpty(getText())) {
      super.onRestoreInstanceState(null);
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  @Override public Parcelable onSaveInstanceState() {
    // If the user changes orientation while they are editing, just roll back the selection.
    clearSelectedChip();
    return super.onSaveInstanceState();
  }

  /**
   * Convenience method: Append the specified text slice to the TextView's display buffer, upgrading it to
   * BufferType.EDITABLE if it was not already editable. Commas are excluded as they are added automatically by the
   * view.
   */
  @Override public void append(final CharSequence text, final int start, final int end) {
    // We don't care about watching text changes while appending.
    if (mTextWatcher != null) {
      removeTextChangedListener(mTextWatcher);
    }
    super.append(text, start, end);
    if (!TextUtils.isEmpty(text) && TextUtils.getTrimmedLength(text) > 0) {
      String displayString = text.toString();
      if (!displayString.trim().endsWith(String.valueOf(COMMIT_CHAR_COMMA))) {
        // We have no separator, so we should add it
        super.append(SEPARATOR, 0, SEPARATOR.length());
        displayString += SEPARATOR;
      }
      if (!TextUtils.isEmpty(displayString) && TextUtils.getTrimmedLength(displayString) > 0) {
        mPendingChipsCount++;
        mPendingChips.add(displayString);
      }
    }
    // Put a message on the queue to make sure we ALWAYS handle pending
    // chips.
    if (mPendingChipsCount > 0) {
      postHandlePendingChips();
    }
    mHandler.post(mAddTextWatcher);
  }

  @Override
  public void onFocusChanged(final boolean hasFocus, final int direction, final Rect previous) {
    super.onFocusChanged(hasFocus, direction, previous);
    if (!hasFocus) {
      if (mFocusBehavior.contains(FocusBehavior.SHRINK_WHEN_LOST_FOCUS)) {
        //shrink();
      }
    } else if (mFocusBehavior.contains(FocusBehavior.EXPAND_WHEN_GOT_FOCUS)) {
      //expand();
    }
  }

  private int getExcessTopPadding() {
    if (sExcessTopPadding == -1) {
      sExcessTopPadding = (int) (mChipHeight + mLineSpacingExtra);
    }
    return sExcessTopPadding;
  }

  @Override public <T extends ListAdapter & Filterable> void setAdapter(final T adapter) {
    super.setAdapter(adapter);
    //((BaseRecipientAdapter) adapter).registerUpdateObserver(
    //    new BaseRecipientAdapter.EntriesUpdatedObserver() {
    //      @Override public void onChanged(final List<RecipientEntry> entries) {
    //        // Scroll the chips field to the top of the screen so
    //        // that the user can see as many results as possible.
    //        if (entries != null && entries.size() > 0) {
    //          scrollBottomIntoView();
    //        }
    //      }
    //    });
  }

  private void scrollBottomIntoView() {
    if (mScrollView != null && mShouldShrink) {
      final int[] location = new int[2];
      getLocationOnScreen(location);
      final int height = getHeight();
      final int currentPos = location[1] + height;
      // Desired position shows at least 1 line of chips below the action
      // bar. We add excess padding to make sure this is always below other
      // content.
      final int desiredPos = (int) mChipHeight + mActionBarHeight + getExcessTopPadding();
      if (currentPos > desiredPos) {
        mScrollView.scrollBy(0, currentPos - desiredPos);
      }
    }
  }

  @Override public void performValidation() {
    // Do nothing. Chips handles its own validation.
  }

  private CharSequence ellipsizeText(final CharSequence text, final TextPaint paint,
      final float maxWidth) {
    paint.setTextSize(mChipFontSize);
    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END);
  }

  private Bitmap createSelectedChip(final ChipItem contact, final TextPaint paint) {
    // Ellipsize the text so that it takes AT MOST the entire width of the
    // autocomplete text entry area. Make sure to leave space for padding
    // on the sides.
    final int height = (int) mChipHeight;
    final int deleteWidth = height;
    final float[] widths = new float[1];
    paint.getTextWidths(" ", widths);
    final String createChipDisplayText = createChipDisplayText(contact);
    final float calculateAvailableWidth = calculateAvailableWidth();
    final CharSequence ellipsizedText = ellipsizeText(createChipDisplayText, paint,
        calculateAvailableWidth - deleteWidth - widths[0]);
    // Make sure there is a minimum chip width so the user can ALWAYS
    // tap a chip without difficulty.
    final int width = Math.max(deleteWidth * 2,
        (int) Math.floor(paint.measureText(ellipsizedText, 0, ellipsizedText.length()))
            + mChipPadding * 2
            + deleteWidth);
    // Create the background of the chip.
    final Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(tmpBitmap);

    if (mChipBackgroundPressed != null) {
      mChipBackgroundPressed.setBounds(0, 0, width, height);
      mChipBackgroundPressed.draw(canvas);
      paint.setColor(sSelectedTextColor);
      // Vertically center the text in the chip.
      canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding,
          getTextYOffset(paint, canvas), paint);
      // Make the delete a square.
      final Rect backgroundPadding = new Rect();
      mChipBackgroundPressed.getPadding(backgroundPadding);
      mChipDelete.setBounds(width - deleteWidth + backgroundPadding.left, backgroundPadding.top,
          width - backgroundPadding.right, height - backgroundPadding.bottom);
      mChipDelete.draw(canvas);
    }
    return tmpBitmap;
  }

  private Bitmap createUnselectedChip(final ChipItem contact, final TextPaint paint,
      final boolean leaveBlankIconSpacer) {
    // ChipItem chip

    final int height = (int) mChipHeight;
    //int iconWidth = 32dp;
    int iconWidth = 0;
    final float[] widths = new float[1];
    paint.getTextWidths(" ", widths);
    final float availableWidth = calculateAvailableWidth();
    final String chipDisplayText = createChipDisplayText(contact);
    final CharSequence ellipsizedText =
        ellipsizeText(chipDisplayText, paint, availableWidth - iconWidth - widths[0]);
    // Make sure there is a minimum chip width so the user can ALWAYS
    // tap a chip without difficulty.
    final int width = Math.max(iconWidth * 2,
        (int) Math.floor(paint.measureText(ellipsizedText, 0, ellipsizedText.length()))
            + mChipPadding * 2
            + iconWidth);
    // Create the background of the chip.
    final Bitmap tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(tmpBitmap);
    final Drawable background = getChipBackground(contact);

    if (background != null) {
      background.setBounds(0, 0, width, height);
      background.draw(canvas);

      paint.setColor(getContext().getResources().getColor(android.R.color.black));
      Rect textBounds = new Rect();
      paint.getTextBounds((String) ellipsizedText, 0, ellipsizedText.length(), textBounds);
      canvas.drawText(ellipsizedText, 0, ellipsizedText.length(), mChipPadding + iconWidth,
          getTextYOffset(paint, canvas), paint);

      //if (mChipIcon != null) {
      //  drawIcon(canvas, width, height, iconWidth, paint);
      //}
    }
    return tmpBitmap;
  }

  private void drawIcon(Canvas canvas, int width, int height, int iconWidth, Paint paint) {
    final Rect backgroundPadding = new Rect();
    mChipBackground.getPadding(backgroundPadding);

    //drawIconBackground(canvas, width, paint, backgroundPadding);
    drawIconBitmap(canvas, width, height, iconWidth, paint, backgroundPadding);
  }

  private void drawIconBitmap(Canvas canvas, int width, int height, int iconWidth, Paint paint,
      Rect backgroundPadding) {
    //Draw the photo on the left side.
    //Bitmap iconBitmap = Bitmap.createBitmap(mChipIcon.getIntrinsicWidth(), mChipIcon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
    //Bitmap scaledIconBitMap = scaleDown(iconBitmap, (float) height * 0.70f, true);
    //iconBitmap.recycle();

    final RectF src =
        new RectF(0, 0, mDefaultContactPhoto.getWidth(), mDefaultContactPhoto.getHeight());

    final RectF dst = new RectF(0 + backgroundPadding.left, 0 + backgroundPadding.top,
        iconWidth + backgroundPadding.right, height - backgroundPadding.bottom);
    final Matrix matrix = new Matrix();
    matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);
    canvas.drawBitmap(mDefaultContactPhoto, matrix, paint);
  }

  private Bitmap scaleDown(Bitmap realImage, float maxImageSize, boolean filter) {
    float ratio =
        Math.min(maxImageSize / realImage.getWidth(), maxImageSize / realImage.getHeight());
    int width = Math.round(ratio * realImage.getWidth());
    int height = Math.round(ratio * realImage.getHeight());
    return Bitmap.createScaledBitmap(realImage, width, height, filter);
  }

  private void drawIconBackground(Canvas canvas, int width, Paint paint, Rect backgroundPadding) {
    int height = calculateChipHeight(backgroundPadding.top, backgroundPadding.bottom);

    paint.setColor(mChipIconBackgroundColor);
    int radius = height / 2;
    //float circleX = mShowIconOnLeft ? (x + radius) : (x + mChipWidth - radius);
    float circleX = width + radius;

    // The y coordinate is always just one radius distance from the top
    canvas.drawCircle(circleX, backgroundPadding.top + radius, radius, paint);

    //paint.setColor(mTextColor);
  }

  private int calculateChipHeight(int top, int bottom) {
    return mChipHeight != -1 ? (int) mChipHeight : bottom - top;
  }

  /**
   * Get the background drawable for a RecipientChip.
   */
  Drawable getChipBackground(final ChipItem contact) {
    //return contact.isValid() ? mChipBackground : mInvalidChipBackground;
    return mChipBackground;
  }

  private static float getTextYOffset(final TextPaint paint, Canvas canvas) {
    return (int) ((canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2));
  }

  private DrawableRecipientChip constructChipSpan(final ChipItem contact, final boolean pressed,
      final boolean leaveIconSpace) throws NullPointerException {
    // ChipItem chip

    if (mChipBackground == null) {
      throw new NullPointerException(
          "Unable to render any chips as setChipDimensions was not called.");
    }
    final TextPaint paint = getPaint();
    final float defaultSize = paint.getTextSize();
    final int defaultColor = paint.getColor();
    Bitmap tmpBitmap;
    if (pressed) {
      tmpBitmap = createSelectedChip(contact, paint);
    } else {
      tmpBitmap = createUnselectedChip(contact, paint, leaveIconSpace);
    }
    // Pass the full text, un-ellipsized, to the chip.
    final Drawable result = new BitmapDrawable(getResources(), tmpBitmap);
    result.setBounds(0, 0, tmpBitmap.getWidth(), tmpBitmap.getHeight());
    final DrawableRecipientChip recipientChip = new VisibleRecipientChip(result, contact);
    // Return text to the original size.
    paint.setTextSize(defaultSize);
    paint.setColor(defaultColor);
    return recipientChip;
  }

  /**
   * Calculate the bottom of the line the chip will be located on using: 1) which line the chip appears on 2) the
   * height of a chip 3) padding built into the edit text view
   */
  private int calculateOffsetFromBottom(final int line) {
    // Line offsets start at zero.
    final int actualLine = getLineCount() - (line + 1);
    return -(actualLine * (int) mChipHeight + getPaddingBottom() + getPaddingTop())
        + getDropDownVerticalOffset();
  }

  /**
   * Get the max amount of space a chip can take up. The formula takes into account the width of the EditTextView, any
   * view padding, and padding that will be added to the chip.
   */
  private float calculateAvailableWidth() {
    return getWidth() - getPaddingLeft() - getPaddingRight() - mChipPadding * 2;
  }

  private void setChipDimensions(final Context context, final AttributeSet attrs) {
    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ChipsEditText, 0, 0);
    final Resources resources = getContext().getResources();

    mChipBackground = a.getDrawable(R.styleable.ChipsEditText_chipBackground);
    mChipBackgroundPressed = a.getDrawable(R.styleable.ChipsEditText_chipBackgroundPressed);
    mChipDelete = a.getDrawable(R.styleable.ChipsEditText_chipDelete);
    mChipPadding = a.getDimensionPixelSize(R.styleable.ChipsEditText_chipPadding, -1);
    mChipHeight = a.getDimensionPixelSize(R.styleable.ChipsEditText_chipHeight, -1);
    mChipFontSize = a.getDimensionPixelSize(R.styleable.ChipsEditText_chipFontSize, -1);
    mInvalidChipBackground = a.getDrawable(R.styleable.ChipsEditText_invalidChipBackground);
    mChipIcon = a.getDrawable(R.styleable.ChipsEditText_chipIcon);
    mChipIconBackgroundColor = a.getColor(R.styleable.ChipsEditText_chipIconBackgroundColor, -1);

    mLineSpacingExtra = resources.getDimension(R.dimen.line_spacing_extra);

    if (mChipBackground == null) {
      mChipBackground = resources.getDrawable(R.drawable.chip_background);
    }
    if (mChipBackgroundPressed == null) {
      mChipBackgroundPressed = resources.getDrawable(R.drawable.chip_background_selected);
    }
    if (mChipDelete == null) {
      mChipDelete = resources.getDrawable(R.drawable.chip_delete);
    }
    if (mChipPadding == -1) {
      mChipPadding = (int) resources.getDimension(R.dimen.chip_padding);
    }
    if (mChipHeight == -1) {
      mChipHeight = resources.getDimension(R.dimen.chip_height);
    }
    if (mChipFontSize == -1) {
      mChipFontSize = resources.getDimension(R.dimen.chip_text_size);
    }
    if (mInvalidChipBackground == null) {
      mInvalidChipBackground = resources.getDrawable(R.drawable.chip_background_invalid);
    }
    if (mChipIconBackgroundColor == -1) {
      mChipIconBackgroundColor = Color.parseColor("#000000");
    }

    mDefaultContactPhoto = BitmapFactory.decodeResource(resources, R.drawable.ic_contact_picture);

    final TypedValue tv = new TypedValue();
    if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
      mActionBarHeight =
          TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
    }
    a.recycle();
  }

  void setChipBackground(final Drawable chipBackground) {
    mChipBackground = chipBackground;
  }

  void setChipHeight(final int height) {
    mChipHeight = height;
  }

  /**
   * Set whether to shrink the recipients field such that at most one line of recipients chips are shown when the
   * field loses focus. By default, the number of displayed recipients will be limited and a "more" chip will be shown
   * when focus is lost.
   */

  @Override public void onSizeChanged(final int width, final int height, final int oldw,
      final int oldh) {
    super.onSizeChanged(width, height, oldw, oldh);
    if (width != 0 && height != 0) {
      if (mPendingChipsCount > 0) {
        postHandlePendingChips();
      } else {
        checkChipWidths();
      }
    }
    // Try to find the scroll view parent, if it exists.
    if (mScrollView == null && !mTriedGettingScrollView) {
      ViewParent parent = getParent();
      while (parent != null && !(parent instanceof ScrollView)) {
        parent = parent.getParent();
      }
      if (parent != null) {
        mScrollView = (ScrollView) parent;
      }
      mTriedGettingScrollView = true;
    }
  }

  private void postHandlePendingChips() {
    mHandler.removeCallbacks(mHandlePendingChips);
    mHandler.post(mHandlePendingChips);
  }

  private void checkChipWidths() {
    // Check the widths of the associated chips.
    final DrawableRecipientChip[] chips = getSortedRecipients();
    if (chips != null) {
      Rect bounds;
      for (final DrawableRecipientChip chip : chips) {
        bounds = chip.getBounds();
        if (getWidth() > 0 && bounds.right - bounds.left > getWidth()) {
          // Need to redraw that chip.
          replaceChip(chip, chip.getEntry());
        }
      }
    }
  }

  void handlePendingChips() {
    if (getViewWidth() <= 0){
      // The widget has not been sized yet.
      // This will be called as a result of onSizeChanged
      // at a later point.
      return;
    }
    if (mPendingChipsCount <= 0) {
      return;
    }
    synchronized (mPendingChips) {
      final Editable editable = getText();
      // Tokenize!
      if (mPendingChipsCount <= MAX_CHIPS_PARSED) {
        for (int i = 0; i < mPendingChips.size(); i++) {
          final String current = mPendingChips.get(i);
          final int tokenStart = editable.toString().indexOf(current);
          // Always leave a space at the end between tokens.
          int tokenEnd = tokenStart + current.length() - 1;
          if (tokenStart >= 0) {
            // When we have a valid token, include it with the token
            // to the left.
            if (tokenEnd < editable.length() - 2
                && editable.charAt(tokenEnd) == COMMIT_CHAR_COMMA) {
              tokenEnd++;
            }
            createReplacementChip(tokenStart, tokenEnd, editable, i < CHIP_LIMIT || !mShouldShrink);
          }
          mPendingChipsCount--;
        }
        sanitizeEnd();
      } else {
        mNoChips = true;
      }
      if (mTemporaryRecipients != null
          && mTemporaryRecipients.size() > 0
          && mTemporaryRecipients.size() <= 50 /*MAX LOOKUPS*/) {
        if (hasFocus() || mTemporaryRecipients.size() < CHIP_LIMIT) {
          mTemporaryRecipients = null;
        } else {
          // Create the "more" chip
          if (mTemporaryRecipients.size() > CHIP_LIMIT) {
            mTemporaryRecipients = new ArrayList<>(
                mTemporaryRecipients.subList(CHIP_LIMIT, mTemporaryRecipients.size()));
          } else {
            mTemporaryRecipients = null;
          }
        }
      } else {
        // There are too many recipients to look up, so just fall back
        // to showing addresses for all of them.
        mTemporaryRecipients = null;
      }
      mPendingChipsCount = 0;
      mPendingChips.clear();
    }
  }

  int getViewWidth() {
    return getWidth();
  }

  /**
   * Remove any characters after the last valid chip.
   */
  void sanitizeEnd() {
    // Don't sanitize while we are waiting for pending chips to complete.
    if (mPendingChipsCount > 0) {
      return;
    }
    // Find the last chip; eliminate any commit characters after it.
    final DrawableRecipientChip[] chips = getSortedRecipients();
    final Spannable spannable = getSpannable();
    if (chips != null && chips.length > 0) {
      int end;
      mMoreChip = getMoreChip();
      if (mMoreChip != null) {
        end = spannable.getSpanEnd(mMoreChip);
      } else {
        end = getSpannable().getSpanEnd(getLastChip());
      }
      final Editable editable = getText();
      final int length = editable.length();
      if (length > end) {
        // See what characters occur after that and eliminate them.
        if (Log.isLoggable(TAG, Log.DEBUG)) {
          Log.d(TAG, "There were extra characters after the last tokenizable entry." + editable);
        }
        editable.delete(end + 1, length);
      }
    }
  }

  /**
   * Create a chip that represents just the email address of a recipient. At some later point, this chip will be
   * attached to a real contact entry, if one exists.
   */
  void createReplacementChip(final int tokenStart, final int tokenEnd, final Editable editable,
      final boolean visible) {
    if (alreadyHasChip(tokenStart, tokenEnd)){
      // There is already a chip present at this location.
      // Don't recreate it.
      return;
    }
    String token = editable.toString().substring(tokenStart, tokenEnd);
    final String trimmedToken = token.trim();
    final int commitCharIndex = trimmedToken.lastIndexOf(COMMIT_CHAR_COMMA);
    if (commitCharIndex != -1 && commitCharIndex == trimmedToken.length() - 1) {
      token = trimmedToken.substring(0, trimmedToken.length() - 1);
    }
    final ChipItem entry = createTokenizedEntry(token);
    if (entry != null) {
      DrawableRecipientChip chip = null;
      try {
        if (!mNoChips) {
          final boolean leaveSpace =
              TextUtils.isEmpty(entry.getTitle()) || TextUtils.equals(entry.getTitle(),
                  entry.getTitle());
          chip = visible ? constructChipSpan(entry, false, leaveSpace)
              : new InvisibleRecipientChip(entry);
        }
      } catch (final NullPointerException e) {
        e.printStackTrace();
        Log.e(TAG, e.getMessage(), e);
      }
      editable.setSpan(chip, tokenStart, tokenEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      // Add this chip to the list of entries "to replace"
      if (chip != null) {
        if (mTemporaryRecipients == null) {
          mTemporaryRecipients = new ArrayList<>();
        }
        chip.setOriginalText(token);
        mTemporaryRecipients.add(chip);
      }
    }
  }

  ChipItem createTokenizedEntry(final String token) {
    if (TextUtils.isEmpty(token)) {
      return null;
    }
    final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(token);
    String display;
    boolean isValid = isValid(token);
    if (isValid && tokens != null && tokens.length > 0) {
      // If we can get a name from tokenizing, then generate an entry from
      // this.
      display = tokens[0].getName();
      if (!TextUtils.isEmpty(display)) {
        return ChipItem.generateEntry(tokens[0].getAddress());
      } else {
        display = tokens[0].getAddress();
        if (!TextUtils.isEmpty(display)) {
          return ChipItem.generateTokenizedEntry(display);
        }
      }
    }
    // Unable to validate the token or to create a valid token from it.
    // Just create a chip the user can edit.
    String validatedToken = null;
    if (mValidator != null && !isValid) {
      // Try fixing up the entry using the validator.
      validatedToken = mValidator.fixText(token).toString();
      if (!TextUtils.isEmpty(validatedToken)) {
        if (validatedToken.contains(token)) {
          // protect against the case of a validator with a null
          // domain,
          // which doesn't add a domain to the token
          final Rfc822Token[] tokenized = Rfc822Tokenizer.tokenize(validatedToken);
          if (tokenized.length > 0) {
            validatedToken = tokenized[0].getAddress();
            isValid = true;
          }
        } else {
          // We ran into a case where the token was invalid and
          // removed
          // by the validator. In this case, just use the original
          // token
          // and let the user sort out the error chip.
          validatedToken = null;
          isValid = false;
        }
      }
    }
    // Otherwise, fallback to just creating an editable email address chip.
    return ChipItem.generateTokenizedEntry(
        !TextUtils.isEmpty(validatedToken) ? validatedToken : token);
  }

  private boolean isValid(final String text) {
    return mValidator == null ? true : mValidator.isValid(text);
  }

  private static String tokenizeAddress(final String destination) {
    final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(destination);
    if (tokens != null && tokens.length > 0) {
      return tokens[0].getAddress();
    }
    return destination;
  }

  @Override public void setTokenizer(final Tokenizer tokenizer) {
    if (tokenizer == null) {
      mTokenizer = new CommaTokenizer();
    } else {
      mTokenizer = tokenizer;
    }
    super.setTokenizer(mTokenizer);
  }

  @Override public void setValidator(final Validator validator) {
    mValidator = validator;
    super.setValidator(validator);
  }

  /**
   * We cannot use the default mechanism for replaceText. Instead, we override onItemClickListener so we can get all
   * the associated contact information including display text, address, and id.
   */
  @Override protected void replaceText(final CharSequence text) {
    return;
  }

  /**
   * Dismiss any selected chips when the back key is pressed.
   */
  @Override public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && mSelectedChip != null) {
      clearSelectedChip();
      return true;
    }
    return super.onKeyPreIme(keyCode, event);
  }

  /**
   * Monitor key presses in this view to see if the user types any commit keys, which consist of ENTER, TAB, or
   * DPAD_CENTER. If the user has entered text that has contact matches and types a commit key, create a chip from the
   * topmost matching contact. If the user has entered text that has no contact matches and types a commit key, then
   * create a chip from the text they have entered.
   */
  @Override public boolean onKeyUp(final int keyCode, final KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_TAB:
        if (event.hasNoModifiers()) {
          if (mSelectedChip != null) {
            clearSelectedChip();
          } else {
            commitDefault();
          }
        }
        break;
    }
    return super.onKeyUp(keyCode, event);
  }

  private boolean focusNext() {
    final View next = focusSearch(View.FOCUS_DOWN);
    if (next != null) {
      next.requestFocus();
      return true;
    }
    return false;
  }

  /**
   * Create a chip from the default selection. If the popup is showing, the default is the first item in the popup
   * suggestions list. Otherwise, it is whatever the user had typed in. End represents where the the tokenizer should
   * search for a token to turn into a chip.
   *
   * @return If a chip was created from a real contact.
   */
  private boolean commitDefault() {
    // If there is no tokenizer, don't try to commit.
    if (mTokenizer == null) {
      return false;
    }
    final Editable editable = getText();
    final int end = getSelectionEnd();
    final int start = mTokenizer.findTokenStart(editable, end);
    if (shouldCreateChip(start, end)) {
      int whatEnd = mTokenizer.findTokenEnd(getText(), start);
      // In the middle of chip; treat this as an edit
      // and commit the whole token.
      whatEnd = movePastTerminators(whatEnd);
      if (whatEnd != getSelectionEnd()) {
        handleEdit(start, whatEnd);
        return true;
      }
      return commitChip(start, end, editable);
    }
    return false;
  }

  private void commitByCharacter() {
    // We can't possibly commit by character if we can't tokenize.
    if (mTokenizer == null) {
      return;
    }
    final Editable editable = getText();
    final int end = getSelectionEnd();
    final int start = mTokenizer.findTokenStart(editable, end);
    if (shouldCreateChip(start, end)) {
      commitChip(start, end, editable);
    }
    setSelection(getText().length());
  }

  private boolean commitChip(final int start, final int end, final Editable editable) {
    final ListAdapter adapter = getAdapter();
    if (adapter != null
        && adapter.getCount() > 0
        && enoughToFilter()
        && end == getSelectionEnd()
        && !isPhoneQuery()) {
      // choose the first entry.
      submitItemAtPosition(0);
      dismissDropDown();
      return true;
    } else {
      int tokenEnd = mTokenizer.findTokenEnd(editable, start);
      if (editable.length() > tokenEnd + 1) {
        final char charAt = editable.charAt(tokenEnd + 1);
        if (charAt == COMMIT_CHAR_COMMA || charAt == COMMIT_CHAR_SEMICOLON) {
          tokenEnd++;
        }
      }
      final String text = editable.toString().substring(start, tokenEnd).trim();
      clearComposingText();
      if (text != null && text.length() > 0 && !text.equals(" ")) {
        //final RecipientEntry entry = createTokenizedEntry(text);
        final ChipItem entry = createTokenizedEntry(text);
        if (entry != null) {
          QwertyKeyListener.markAsReplaced(editable, start, end, "");
          final CharSequence chipText = createChip(entry, false);
          if (chipText != null && start > -1 && end > -1) {
            editable.replace(start, end, chipText);
          }
        }
        // Only dismiss the dropdown if it is related to the text we
        // just committed.
        // For paste, it may not be as there are possibly multiple
        // tokens being added.
        if (end == getSelectionEnd()) {
          dismissDropDown();
        }
        sanitizeBetween();
        return true;
      }
    }
    return false;
  }

  void sanitizeBetween() {
    // Don't sanitize while we are waiting for content to chipify.
    if (mPendingChipsCount > 0) {
      return;
    }
    // Find the last chip.
    final DrawableRecipientChip[] recips = getSortedRecipients();
    if (recips != null && recips.length > 0) {
      final DrawableRecipientChip last = recips[recips.length - 1];
      DrawableRecipientChip beforeLast = null;
      if (recips.length > 1) {
        beforeLast = recips[recips.length - 2];
      }
      int startLooking = 0;
      final int end = getSpannable().getSpanStart(last);
      if (beforeLast != null) {
        startLooking = getSpannable().getSpanEnd(beforeLast);
        final Editable text = getText();
        if (startLooking == -1 || startLooking > text.length() - 1)
        // There is nothing after this chip.
        {
          return;
        }
        if (text.charAt(startLooking) == ' ') {
          startLooking++;
        }
      }
      if (startLooking >= 0 && end >= 0 && startLooking < end) {
        getText().delete(startLooking, end);
      }
    }
  }

  private boolean shouldCreateChip(final int start, final int end) {
    return !mNoChips && hasFocus() && enoughToFilter() && !alreadyHasChip(start, end);
  }

  private boolean alreadyHasChip(final int start, final int end) {
    if (mNoChips) {
      return true;
    }
    final DrawableRecipientChip[] chips =
        getSpannable().getSpans(start, end, DrawableRecipientChip.class);
    if (chips == null || chips.length == 0) {
      return false;
    }
    return true;
  }

  private void handleEdit(final int start, final int end) {
    if (start == -1 || end == -1) {
      // This chip no longer exists in the field.
      dismissDropDown();
      return;
    }
    // This is in the middle of a chip, so select out the whole chip
    // and commit it.
    final Editable editable = getText();
    setSelection(end);
    final String text = getText().toString().substring(start, end);
    if (!TextUtils.isEmpty(text)) {
      final ChipItem entry = ChipItem.generateTokenizedEntry(text);

      QwertyKeyListener.markAsReplaced(editable, start, end, "");
      final CharSequence chipText = createChip(entry, false);
      final int selEnd = getSelectionEnd();
      if (chipText != null && start > -1 && selEnd > -1) {
        editable.replace(start, selEnd, chipText);
      }
    }
    dismissDropDown();
  }

  /**
   * If there is a selected chip, delegate the key events to the selected chip.
   */
  @Override public boolean onKeyDown(final int keyCode, final KeyEvent event) {
    if (mSelectedChip != null && keyCode == KeyEvent.KEYCODE_DEL) {
      removeChip(mSelectedChip, true);
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_DPAD_CENTER:
        if (event.hasNoModifiers()) {
          if (commitDefault()) {
            return true;
          }
          if (mSelectedChip != null) {
            clearSelectedChip();
            return true;
          } else if (focusNext()) {
            return true;
          }
        }
        break;
    }
    return super.onKeyDown(keyCode, event);
  }

  Spannable getSpannable() {
    return getText();
  }

  private int getChipStart(final DrawableRecipientChip chip) {
    return getSpannable().getSpanStart(chip);
  }

  private int getChipEnd(final DrawableRecipientChip chip) {
    return getSpannable().getSpanEnd(chip);
  }

  /**
   * Instead of filtering on the entire contents of the edit box, this subclass method filters on the range from {@link Tokenizer#findTokenStart} to {@link #getSelectionEnd} if the length of that range meets or exceeds {@link #getThreshold} and makes sure that the range is not already a Chip.
   */
  @Override protected void performFiltering(final CharSequence text, final int keyCode) {
    final boolean isCompletedToken = isCompletedToken(text);
    if (enoughToFilter() && !isCompletedToken) {
      final int end = getSelectionEnd();
      final int start = mTokenizer.findTokenStart(text, end);
      // If this is a RecipientChip, don't filter
      // on its contents.
      final Spannable span = getSpannable();
      final DrawableRecipientChip[] chips = span.getSpans(start, end, DrawableRecipientChip.class);
      if (chips != null && chips.length > 0) {
        return;
      }
    } else if (isCompletedToken) {
      return;
    }
    super.performFiltering(text, keyCode);
  }

  boolean isCompletedToken(final CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return false;
    }
    // Check to see if this is a completed token before filtering.
    final int end = text.length();
    final int start = mTokenizer.findTokenStart(text, end);
    final String token = text.toString().substring(start, end).trim();
    if (!TextUtils.isEmpty(token)) {
      final char atEnd = token.charAt(token.length() - 1);
      return atEnd == COMMIT_CHAR_COMMA || atEnd == COMMIT_CHAR_SEMICOLON;
    }
    return false;
  }

  private void clearSelectedChip() {
    if (mSelectedChip != null) {
      unselectChip(mSelectedChip);
      mSelectedChip = null;
    }
    setCursorVisible(true);
  }

  /**
   * Monitor touch events in the RecipientEditTextView. If the view does not have focus, any tap on the view will just
   * focus the view. If the view has focus, determine if the touch target is a recipient chip. If it is and the chip
   * is not selected, select it and clear any other selected chips. If it isn't, then select that chip.
   */
  @Override public boolean onTouchEvent(final MotionEvent event) {
    if (!isFocused()) {
      // Ignore any chip taps until this view is focused.
      return super.onTouchEvent(event);
    }
    boolean handled = super.onTouchEvent(event);
    final int action = event.getAction();
    boolean chipWasSelected = false;
    if (mSelectedChip == null) {
      mGestureDetector.onTouchEvent(event);
    }
    switch (action) {
      case MotionEvent.ACTION_DOWN:
        mIsScrolling = false;
        mStartTouchY = (int) event.getY();
        break;
      case MotionEvent.ACTION_MOVE:
        if (Math.abs(event.getY() - mStartTouchY) > SCROLLING_BOUNDS_THRESHOLD_IN_PIXELS) {
          mIsScrolling = true;
        }
        break;
      case MotionEvent.ACTION_UP:
        if (mIsScrolling) {
          mIsScrolling = false;
          break;
        }
        if (mCopyAddress == null) {
          final float x = event.getX();
          final float y = event.getY();
          final int offset = putOffsetInRange(x, y);
          final DrawableRecipientChip currentChip = findChip(offset);
          if (currentChip != null) {
            if (action == MotionEvent.ACTION_UP) {
              if (mSelectedChip != null && mSelectedChip != currentChip) {
                clearSelectedChip();
                mSelectedChip = selectChip(currentChip);
              } else if (mSelectedChip == null) {
                setSelection(getText().length());
                commitDefault();
                mSelectedChip = selectChip(currentChip);
              } else {
                onClick(mSelectedChip, offset, x, y);
              }
            }
            chipWasSelected = true;
            handled = true;
          } else if (mSelectedChip != null && shouldShowEditableText(mSelectedChip)) {
            chipWasSelected = true;
          }
        }
        if (!chipWasSelected) {
          clearSelectedChip();
        }
        break;
    }
    return handled;
  }

  private void scrollLineIntoView(final int line) {
    if (mScrollView != null) {
      mScrollView.smoothScrollBy(0, calculateOffsetFromBottom(line));
    }
  }

  private int putOffsetInRange(final float x, final float y) {
    final int offset;
    offset = getOffsetForPosition(x, y);
    return putOffsetInRange(offset);
  }

  // the chips ui. This attempts to be "forgiving" to fat finger touches by favoring
  // what comes before the finger.
  private int putOffsetInRange(final int o) {
    int offset = o;
    final Editable text = getText();
    final int length = text.length();
    // Remove whitespace from end to find "real end"
    int realLength = length;
    for (int i = length - 1; i >= 0; i--)
      if (text.charAt(i) == ' ') {
        realLength--;
      } else {
        break;
      }
    // If the offset is beyond or at the end of the text,
    // leave it alone.
    if (offset >= realLength) {
      return offset;
    }
    final Editable editable = getText();
    while (offset >= 0 && findText(editable, offset) == -1 && findChip(offset) == null) {
      // Keep walking backward!
      offset--;
    }
    return offset;
  }

  private static int findText(final Editable text, final int offset) {
    if (text.charAt(offset) != ' ') {
      return offset;
    }
    return -1;
  }

  private DrawableRecipientChip findChip(final int offset) {
    final DrawableRecipientChip[] chips =
        getSpannable().getSpans(0, getText().length(), DrawableRecipientChip.class);
    // Find the chip that contains this offset.
    for (int i = 0; i < chips.length; i++) {
      final DrawableRecipientChip chip = chips[i];
      final int start = getChipStart(chip);
      final int end = getChipEnd(chip);
      if (offset >= start && offset <= end) {
        return chip;
      }
    }
    return null;
  }

  // Use this method to generate text to add to the list of addresses.
  String createAddressText(final ChipItem entry) {
    String display = entry.getTitle();
    String address = entry.getTitle();
    if (TextUtils.isEmpty(display) || TextUtils.equals(display, address)) {
      display = null;
    }
    String trimmedDisplayText;
    if (address != null) {
      // Tokenize out the address in case the address already
      // contained the username as well.
      final Rfc822Token[] tokenized = Rfc822Tokenizer.tokenize(address);
      if (tokenized != null && tokenized.length > 0) {
        address = tokenized[0].getAddress();
      }
    }
    final Rfc822Token token = new Rfc822Token(display, address, null);
    trimmedDisplayText = token.toString().trim();

    final int index = trimmedDisplayText.indexOf(",");
    return mTokenizer != null
        && !TextUtils.isEmpty(trimmedDisplayText)
        && index < trimmedDisplayText.length() - 1 ? (String) mTokenizer.terminateToken(
        trimmedDisplayText) : trimmedDisplayText;
  }

  // Use this method to generate text to display in a chip.
  String createChipDisplayText(final ChipItem entry) {
    //ChipItem chipItem
    String display = entry.getTitle();
    final String address = entry.getTitle();
    if (TextUtils.isEmpty(display) || TextUtils.equals(display, address)) {
      display = null;
    }
    if (!TextUtils.isEmpty(display)) {
      return display;
    } else if (!TextUtils.isEmpty(address)) {
      return address;
    } else {
      return new Rfc822Token(display, address, null).toString();
    }
  }

  private CharSequence createChip(final ChipItem entry, final boolean pressed) {
    final String displayText = createAddressText(entry);
    if (TextUtils.isEmpty(displayText)) {
      return null;
    }
    SpannableString chipText;
    final int textLength = displayText.length() - 1;
    chipText = new SpannableString(displayText);
    if (!mNoChips) {
      try {
        final DrawableRecipientChip chip = constructChipSpan(entry, pressed, false);
        chipText.setSpan(chip, 0, textLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        chip.setOriginalText(chipText.toString());
      } catch (final NullPointerException e) {
        Log.e(TAG, e.getMessage(), e);
        return null;
      }
    }
    return chipText;
  }

  private void submitItemAtPosition(final int position) {
    final ChipItem entry
        = createValidatedEntry((ChipItem) getAdapter().getItem(position));

    if (entry == null) {
      return;
    }

    clearComposingText();
    final int end = getSelectionEnd();
    final int start = mTokenizer.findTokenStart(getText(), end);
    final Editable editable = getText();
    QwertyKeyListener.markAsReplaced(editable, start, end, "");
    final CharSequence chip = createChip(entry, false);
    if (chip != null && start >= 0 && end >= 0) {
      editable.replace(start, end, chip);
    }
    sanitizeBetween();
    if (mChipListener != null) {
      mChipListener.onDataChanged();
    }
  }

  private ChipItem createValidatedEntry(final ChipItem item) {
    if (item == null) {
      return null;
    }
    final ChipItem entry;
    // If the display name and the address are the same, or if this is a
    // valid contact, but the destination is invalid, then make this a fake
    // recipient that is editable.
    if ((TextUtils.isEmpty(item.getTitle())
        || TextUtils.equals(item.getTitle(), item.getTitle())
        || mValidator != null && !mValidator.isValid(item.getTitle()))) {
      entry = ChipItem.generateTokenizedEntry(item.getTitle());
    } else {
      entry = ChipItem.generateEntry(item.getTitle());
    }
    return entry;
  }

  DrawableRecipientChip[] getSortedRecipients() {
    final DrawableRecipientChip[] recips =
        getSpannable().getSpans(0, getText().length(), DrawableRecipientChip.class);
    final ArrayList<DrawableRecipientChip> recipientsList = new ArrayList<>(Arrays.asList(recips));
    final Spannable spannable = getSpannable();
    Collections.sort(recipientsList, new Comparator<DrawableRecipientChip>() {
      @Override
      public int compare(final DrawableRecipientChip first, final DrawableRecipientChip second) {
        final int firstStart = spannable.getSpanStart(first);
        final int secondStart = spannable.getSpanStart(second);
        if (firstStart < secondStart) {
          return -1;
        } else if (firstStart > secondStart) {
          return 1;
        } else {
          return 0;
        }
      }
    });
    return recipientsList.toArray(new DrawableRecipientChip[recipientsList.size()]);
  }

  @Override public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
    return false;
  }

  @Override public void onDestroyActionMode(ActionMode actionMode) {

  }

  @Override public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
    return false;
  }

  /**
   * No chips are selectable.
   */
  @Override public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
    return false;
  }

  ImageSpan getMoreChip() {
    final MoreImageSpan[] moreSpans =
        getSpannable().getSpans(0, getText().length(), MoreImageSpan.class);
    return moreSpans != null && moreSpans.length > 0 ? moreSpans[0] : null;
  }

  private DrawableRecipientChip selectChip(final DrawableRecipientChip currentChip) {
    final int start = getChipStart(currentChip);
    final int end = getChipEnd(currentChip);
    getSpannable().removeSpan(currentChip);
    DrawableRecipientChip newChip;
    try {
      newChip = constructChipSpan(currentChip.getEntry(), true, false);
    } catch (final NullPointerException e) {
      Log.e(TAG, e.getMessage(), e);
      return null;
    }
    final Editable editable = getText();
    QwertyKeyListener.markAsReplaced(editable, start, end, "");
    if (start == -1 || end == -1) {
      Log.d(TAG, "The chip being selected no longer exists but should.");
    } else {
      editable.setSpan(newChip, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    newChip.setSelected(true);
    if (shouldShowEditableText(newChip)) {
      scrollLineIntoView(getLayout().getLineForOffset(getChipStart(newChip)));
    }
    setCursorVisible(false);
    return newChip;
  }

  private boolean shouldShowEditableText(final DrawableRecipientChip currentChip) {
    //final long contactId = currentChip.getContactId();
    //return contactId == RecipientEntry.INVALID_CONTACT
    //    || !isPhoneQuery() && contactId == RecipientEntry.GENERATED_CONTACT;
    return false;
  }

  /**
   * Remove selection from this chip. Unselecting a RecipientChip will render the chip without a delete icon and with
   * an unfocused background. This is called when the RecipientChip no longer has focus.
   */
  private void unselectChip(final DrawableRecipientChip chip) {
    final int start = getChipStart(chip);
    final int end = getChipEnd(chip);
    final Editable editable = getText();
    mSelectedChip = null;
    if (start == -1 || end == -1) {
      Log.w(TAG, "The chip doesn't exist or may be a chip a user was editing");
      setSelection(editable.length());
      commitDefault();
    } else {
      getSpannable().removeSpan(chip);
      QwertyKeyListener.markAsReplaced(editable, start, end, "");
      editable.removeSpan(chip);
      try {
        if (!mNoChips) {
          editable.setSpan(constructChipSpan(chip.getEntry(), false, false), start, end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      } catch (final NullPointerException e) {
        Log.e(TAG, e.getMessage(), e);
      }
    }
    setCursorVisible(true);
    setSelection(editable.length());
  }

  /**
   * Return whether a touch event was inside the delete target of a selected chip. It is in the delete target if: 1)
   * the x and y points of the event are within the delete assset. 2) the point tapped would have caused a cursor to
   * appear right after the selected chip.
   *
   * @return boolean
   */
  private boolean isInDelete(final DrawableRecipientChip chip, final int offset, final float x,
      final float y) {
    // Figure out the bounds of this chip and whether or not
    // the user clicked in the X portion.
    // TODO: Should x and y be used, or removed?
    return chip.isSelected() && offset == getChipEnd(chip);
  }

  /**
   * Remove the chip and any text associated with it from the RecipientEditTextView.
   */
  void removeChip(final DrawableRecipientChip chip, final boolean alsoNotifyAboutDataChanges) {
    if (!alsoNotifyAboutDataChanges) {
      --mPreviousChipsCount;
    }
    final Spannable spannable = getSpannable();
    final int spanStart = spannable.getSpanStart(chip);
    final int spanEnd = spannable.getSpanEnd(chip);
    final Editable text = getText();
    int toDelete = spanEnd;
    final boolean wasSelected = chip == mSelectedChip;
    // Clear that there is a selected chip before updating any text.
    if (wasSelected) {
      mSelectedChip = null;
    }
    // Always remove trailing spaces when removing a chip.
    while (toDelete >= 0 && toDelete < text.length() && text.charAt(toDelete) == ' ') {
      toDelete++;
    }
    spannable.removeSpan(chip);
    if (spanStart >= 0 && toDelete > 0) {
      text.delete(spanStart, toDelete);
    }
    if (wasSelected) {
      clearSelectedChip();
    }
  }

  /**
   * Replace this currently selected chip with a new chip that uses the contact data provided.
   */
  void replaceChip(final DrawableRecipientChip chip, final ChipItem entry) {
    final boolean wasSelected = chip == mSelectedChip;
    if (wasSelected) {
      mSelectedChip = null;
    }
    final int start = getChipStart(chip);
    final int end = getChipEnd(chip);
    getSpannable().removeSpan(chip);
    final Editable editable = getText();
    final CharSequence chipText = createChip(entry, false);
    if (chipText != null) {
      if (start == -1 || end == -1) {
        Log.e(TAG, "The chip to replace does not exist but should.");
        editable.insert(0, chipText);
      } else if (!TextUtils.isEmpty(chipText)) {
        // There may be a space to replace with this chip's new
        // associated space. Check for it
        int toReplace = end;
        while (toReplace >= 0
            && toReplace < editable.length()
            && editable.charAt(toReplace) == ' ') {
          toReplace++;
        }
        editable.replace(start, toReplace, chipText);
      }
    }
    setCursorVisible(true);
    if (wasSelected) {
      clearSelectedChip();
    }
    if (mChipListener != null) {
      mChipListener.onDataChanged();
    }
  }

  /**
   * Handle click events for a chip. When a selected chip receives a click event, see if that event was in the delete
   * icon. If so, delete it. Otherwise, unselect the chip.
   */
  public void onClick(final DrawableRecipientChip chip, final int offset, final float x,
      final float y) {
    if (chip.isSelected()) {
      if (isInDelete(chip, offset, x, y)) {
        removeChip(chip, true);
      } else {
        clearSelectedChip();
      }
    }
  }

  private boolean chipsPending() {
    return mPendingChipsCount > 0 || mRemovedSpans != null && mRemovedSpans.size() > 0;
  }

  @Override public void removeTextChangedListener(final TextWatcher watcher) {
    mTextWatcher = null;
    super.removeTextChangedListener(watcher);
  }

  public boolean lastCharacterIsCommitCharacter(final CharSequence s) {
    char last;
    final int end = getSelectionEnd() == 0 ? 0 : getSelectionEnd() - 1;
    final int len = length() - 1;
    if (end != len) {
      last = s.charAt(end);
    } else {
      last = s.charAt(len);
    }
    return last == COMMIT_CHAR_COMMA || last == COMMIT_CHAR_SEMICOLON;
  }

  public boolean isGeneratedContact(final DrawableRecipientChip chip) {
    //final long contactId = chip.getContactId();
    //return contactId == RecipientEntry.INVALID_CONTACT
    //    || !isPhoneQuery() && contactId == RecipientEntry.GENERATED_CONTACT;

    return true;
  }

  /**
   * Handles pasting a {@link ClipData} to this {@link ChipsEditText}.
   */
  private void handlePasteClip(final ClipData clip) {
    removeTextChangedListener(mTextWatcher);
    if (clip != null && clip.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
      for (int i = 0; i < clip.getItemCount(); i++) {
        final CharSequence paste = clip.getItemAt(i).getText();
        if (paste != null) {
          final int start = getSelectionStart();
          final int end = getSelectionEnd();
          final Editable editable = getText();
          if (start >= 0 && end >= 0 && start != end) {
            editable.append(paste, start, end);
          } else {
            editable.insert(end, paste);
          }
          handlePasteAndReplace();
        }
      }
    }
    mHandler.post(mAddTextWatcher);
  }

  @Override public boolean onTextContextMenuItem(final int id) {
    if (id == android.R.id.paste) {
      handlePasteClip(Utils.getTextFromClipboard(getContext()));
      return true;
    }
    return super.onTextContextMenuItem(id);
  }

  private void handlePasteAndReplace() {
    final ArrayList<DrawableRecipientChip> created = handlePaste();
    if (created != null && created.size() > 0) {
      // Perform reverse lookups on the pasted contacts.
      //final IndividualReplacementTask replace = new IndividualReplacementTask();
      //replace.execute(created);
    }
  }

  ArrayList<DrawableRecipientChip> handlePaste() {
    final String text = getText().toString();
    final int originalTokenStart = mTokenizer.findTokenStart(text, getSelectionEnd());
    final String lastAddress = text.substring(originalTokenStart);
    int tokenStart = originalTokenStart;
    int prevTokenStart = 0;
    DrawableRecipientChip findChip = null;
    final ArrayList<DrawableRecipientChip> created = new ArrayList<>();
    if (tokenStart != 0) {
      // There are things before this!
      while (tokenStart != 0 && findChip == null && tokenStart != prevTokenStart) {
        prevTokenStart = tokenStart;
        tokenStart = mTokenizer.findTokenStart(text, tokenStart);
        findChip = findChip(tokenStart);
        if (tokenStart == originalTokenStart && findChip == null) {
          break;
        }
      }
      if (tokenStart != originalTokenStart) {
        if (findChip != null) {
          tokenStart = prevTokenStart;
        }
        int tokenEnd;
        DrawableRecipientChip createdChip;
        while (tokenStart < originalTokenStart) {
          tokenEnd = movePastTerminators(mTokenizer.findTokenEnd(getText().toString(), tokenStart));
          commitChip(tokenStart, tokenEnd, getText());
          createdChip = findChip(tokenStart);
          if (createdChip == null) {
            break;
          }
          // +1 for the space at the end.
          tokenStart = getSpannable().getSpanEnd(createdChip) + 1;
          created.add(createdChip);
        }
      }
    }
    // Take a look at the last token. If the token has been completed with a
    // commit character, create a chip.
    if (isCompletedToken(lastAddress)) {
      final Editable editable = getText();
      tokenStart = editable.toString().indexOf(lastAddress, originalTokenStart);
      commitChip(tokenStart, editable.length(), editable);
      created.add(findChip(tokenStart));
    }
    return created;
  }

  int movePastTerminators(int tokenEnd) {
    if (tokenEnd >= length()) {
      return tokenEnd;
    }
    final char atEnd = getText().toString().charAt(tokenEnd);
    if (atEnd == COMMIT_CHAR_COMMA || atEnd == COMMIT_CHAR_SEMICOLON) {
      tokenEnd++;
    }
    // This token had not only an end token character, but also a space
    // separating it from the next token.
    if (tokenEnd < length() && getText().toString().charAt(tokenEnd) == ' ') {
      tokenEnd++;
    }
    return tokenEnd;
  }

  @Override public boolean onDown(final MotionEvent e) {
    return false;
  }

  @Override
  public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
      final float velocityY) {
    return false;
  }

  @Override public void onLongPress(final MotionEvent event) {
    if (mSelectedChip != null) {
      return;
    }
    final float x = event.getX();
    final float y = event.getY();
    final int offset = putOffsetInRange(x, y);
    final DrawableRecipientChip currentChip = findChip(offset);
    if (currentChip != null) {
      showCopyDialog(currentChip.getEntry().getTitle());
    }
  }

  // The following methods are used to provide some functionality on older versions of Android
  // These methods were copied out of JB MR2's TextView
  private int supportGetOffsetForPosition(final float x, final float y) {
    if (getLayout() == null) {
      return -1;
    }
    final int line = supportGetLineAtCoordinate(y);
    final int offset = supportGetOffsetAtCoordinate(line, x);
    return offset;
  }

  private float supportConvertToLocalHorizontalCoordinate(float x) {
    x -= getTotalPaddingLeft();
    // Clamp the position to inside of the view.
    x = Math.max(0.0f, x);
    x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
    x += getScrollX();
    return x;
  }

  private int supportGetLineAtCoordinate(float y) {
    y -= getTotalPaddingLeft();
    // Clamp the position to inside of the view.
    y = Math.max(0.0f, y);
    y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
    y += getScrollY();
    return getLayout().getLineForVertical((int) y);
  }

  private int supportGetOffsetAtCoordinate(final int line, float x) {
    x = supportConvertToLocalHorizontalCoordinate(x);
    return getLayout().getOffsetForHorizontal(line, x);
  }

  private void showCopyDialog(final String address) {
    if (!mAttachedToWindow) {
      return;
    }

    mCopyAddress = address;

    String[] options = new String[] { getContext().getResources().getString(R.string.copy) };

    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
    builder.setItems(options, new DialogInterface.OnClickListener() {
      @Override public void onClick(DialogInterface dialogInterface, int i) {
        Utils.copyToClipboard(getContext(), mCopyAddress);
      }
    });
    builder.setOnDismissListener(this);
    builder.show();
  }

  @Override
  public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
      final float distanceY) {
    // Do nothing.
    return false;
  }

  @Override public void onShowPress(final MotionEvent e) {
    // Do nothing.
  }

  @Override public boolean onSingleTapUp(final MotionEvent e) {
    // Do nothing.
    return false;
  }

  @Override public void onDismiss(final DialogInterface dialog) {
    mCopyAddress = null;
  }

  protected boolean isPhoneQuery() {
    //return getAdapter() != null
    //    && getAdapter().getQueryType() == BaseRecipientAdapter.QUERY_TYPE_PHONE;
    return false;
  }

  @Override public ListAdapter getAdapter() {
    return super.getAdapter();
  }

  private class MoreImageSpan extends ImageSpan {
    MoreImageSpan(final Drawable b) {
      super(b);
    }
  }

  private class RecipientTextWatcher implements TextWatcher {
    @Override public void afterTextChanged(final Editable s) {
      // If the text has been set to null or empty, make sure we remove
      // all the spans we applied.
      if (TextUtils.isEmpty(s)) {
        // Remove all the chips spans.
        final Spannable spannable = getSpannable();
        final DrawableRecipientChip[] chips =
            spannable.getSpans(0, getText().length(), DrawableRecipientChip.class);
        for (final DrawableRecipientChip chip : chips)
          spannable.removeSpan(chip);
        if (mMoreChip != null) {
          spannable.removeSpan(mMoreChip);
        }
        return;
      }
      // Get whether there are any recipients pending addition to the
      // view. If there are, don't do anything in the text watcher.
      if (chipsPending()) {
        return;
      }
      // If the user is editing a chip, don't clear it.
      if (mSelectedChip != null) {
        if (!isGeneratedContact(mSelectedChip)) {
          setCursorVisible(true);
          setSelection(getText().length());
          clearSelectedChip();
        } else {
          return;
        }
      }
      final int length = s.length();
      // Make sure there is content there to parse and that it is
      // not just the commit character.
      if (length > 1) {
        if (lastCharacterIsCommitCharacter(s)) {
          commitByCharacter();
          return;
        }
        char last;
        final int end = getSelectionEnd() == 0 ? 0 : getSelectionEnd() - 1;
        final int len = length() - 1;
        if (end != len) {
          last = s.charAt(end);
        } else {
          last = s.charAt(len);
        }
        if (last == COMMIT_CHAR_SPACE) {
          if (!isPhoneQuery()) {
            // Check if this is a valid email address. If it is,
            // commit it.
            final String text = getText().toString();
            final int tokenStart = mTokenizer.findTokenStart(text, getSelectionEnd());
            final String sub =
                text.substring(tokenStart, mTokenizer.findTokenEnd(text, tokenStart));
            if (!TextUtils.isEmpty(sub) && mValidator != null && mValidator.isValid(sub)) {
              commitByCharacter();
            }
          }
        }
      }
    }

    @Override public void onTextChanged(final CharSequence s, final int start, final int before,
        final int count) {
      // The user deleted some text OR some text was replaced; check to
      // see if the insertion point is on a space
      // following a chip.
      if (count != before) {
        final DrawableRecipientChip[] chips =
            getSpannable().getSpans(0, getText().length(), DrawableRecipientChip.class);
        final int chipsCount = chips.length;
        if (mPreviousChipsCount > chipsCount && mChipListener != null) {
          mChipListener.onDataChanged();
        }
        mPreviousChipsCount = chipsCount;
      }
      if (before - count == 1) {
        // If the item deleted is a space, and the thing before the
        // space is a chip, delete the entire span.
        final int selStart = getSelectionStart();
        final DrawableRecipientChip[] repl =
            getSpannable().getSpans(selStart, selStart, DrawableRecipientChip.class);
        if (repl.length > 0) {
          // There is a chip there! Just remove it.
          final Editable editable = getText();
          // Add the separator token.
          final int tokenStart = mTokenizer.findTokenStart(editable, selStart);
          int tokenEnd = mTokenizer.findTokenEnd(editable, tokenStart);
          tokenEnd = tokenEnd + 1;
          if (tokenEnd > editable.length()) {
            tokenEnd = editable.length();
          }
          editable.delete(tokenStart, tokenEnd);
          getSpannable().removeSpan(repl[0]);
        }
      } else if (count > before) {
        if (mSelectedChip != null && isGeneratedContact(mSelectedChip)) {
          if (lastCharacterIsCommitCharacter(s)) {
            commitByCharacter();
            return;
          }
        }
      }
    }

    @Override public void beforeTextChanged(final CharSequence s, final int start, final int count,
        final int after) {
    }
  }

  public void setChipListener(final IChipListener chipListener) {
    mChipListener = chipListener;
  }

  public void addRecipient(final ChipItem entry, final boolean alsoNotifyAboutDataChanges) {
    if (entry == null) {
      return;
    }
    final Editable editable = getText();
    final CharSequence chip = createChip(entry, false);
    if (!alsoNotifyAboutDataChanges) {
      ++mPreviousChipsCount;
    }
    if (chip != null) {
      editable.append(chip);
    }
    sanitizeBetween();
  }

  public List<String> getAllChipsValue() {
    final List<String> result = new ArrayList<>();
    final DrawableRecipientChip[] chips =
        getSpannable().getSpans(0, getText().length(), DrawableRecipientChip.class);

    for (int i = 0; i < chips.length; i++) {
      final ChipItem chipItem = chips[i].getEntry();
      result.add(chipItem.getTitle());
    }
    return result;
  }

  public void setChipsList(final List<String> chipsList) {
    post(new Runnable() {
      @Override public void run() {
        for (final String tag : chipsList) {
          ChipItem chipItem = ChipItem.generateTokenizedEntry(tag);
          addRecipient(chipItem, true);
        }
      }
    });
  }

  public void removeAllRecipients(final boolean alsoNotifyAboutDataChanges) {
    final DrawableRecipientChip[] chips =
        getSpannable().getSpans(0, getText().length(), DrawableRecipientChip.class);
    for (final DrawableRecipientChip chip : chips)
      removeChip(chip, alsoNotifyAboutDataChanges);
  }

  public void setFocusBehavior(final EnumSet<FocusBehavior> focusBehavior) {
    mFocusBehavior.clear();
    mFocusBehavior.addAll(focusBehavior);
  }
}