# Chips-input
This project contains modified and reduced type of Android Open Source Project (RecipientEditText.java)

![alt text][logo]

[logo]: https://github.com/DjamshidDjurayev/chips-input/blob/master/example.jpg

# How to use it: 

In your activity add this.

```java
    suggestions.add(new ChipItem(1, "Suggest1"));
    suggestions.add(new ChipItem(2, "Suggest2"));
    suggestions.add(new ChipItem(3, "Suggest3"));
    suggestions.add(new ChipItem(4, "Suggest4"));
    suggestions.add(new ChipItem(5, "Suggest5"));

    chips.add("Chip1");
    chips.add("Chip2");
    chips.add("Chip3");
    chips.add("Chip4");
    chips.add("Chip5");

    SuggestionsAdapter suggestionsAdapter
        = new SuggestionsAdapter(this, suggestions);

    chipsInput.setmChipAllowDuplicate(false);
    chipsInput.setThreshold(2);
    chipsInput.setAdapter(suggestionsAdapter);
    chipsInput.setChipsList(chips);
```
Then create your xml files and add this.
``` xml
<co.djuraev.chipsinput.chips.RecipientEditTextView
        android:id="@+id/tag_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@null"
        android:hint="Input tags"
        android:paddingBottom="16dp"
        android:paddingEnd="16dp"
        android:paddingLeft="16dp"
        android:paddingRight="16dp"
        android:paddingStart="16dp"
        android:paddingTop="16dp"
        android:scrollbars="vertical"
        android:textAllCaps="false"
        android:textColor="#8f000000"
        android:textColorHint="#aeaeae"
        android:textSize="16sp"
        app:chipBackground="@drawable/chip_background_drawable"
        app:chipBackgroundPressed="@drawable/chip_background_drawable_pressed"
        app:chipDelete="@drawable/baseline_cancel_white_48dp"
        app:chipIcon="@drawable/ic_contact_picture"
        app:chipAllowDuplicate="true"
        />
```

