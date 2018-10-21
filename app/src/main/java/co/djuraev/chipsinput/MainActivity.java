package co.djuraev.chipsinput;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import co.djuraev.chipsinput.chips.ChipItem;
import co.djuraev.chipsinput.chips.RecipientEditTextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  @BindView(R.id.tag_input) RecipientEditTextView chipsInput;

  private List<ChipItem> suggestions = new ArrayList<>();
  private final List<String> chips = new ArrayList<>();

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    suggestions.add(new ChipItem(1, "Suggest1"));
    suggestions.add(new ChipItem(2, "Suggest2"));
    suggestions.add(new ChipItem(3, "Suggest3"));
    suggestions.add(new ChipItem(4, "Suggest4"));
    suggestions.add(new ChipItem(5, "Suggest5"));

    chips.add("Chip1");
    chips.add("Chip1");
    chips.add("Chip3");
    chips.add("Chip5");
    chips.add("Chip5");

    SuggestionsAdapter suggestionsAdapter
        = new SuggestionsAdapter(this, suggestions);

    chipsInput.setmChipAllowDuplicate(false);
    chipsInput.setThreshold(2);
    chipsInput.setAdapter(suggestionsAdapter);
    chipsInput.setChipsList(chips);
  }

  @OnClick(R.id.button_chips) protected void onChipsButtonClicked() {
    Toast.makeText(this, chipsInput.getAllChipsValue().toString(), Toast.LENGTH_LONG).show();
  }
}
