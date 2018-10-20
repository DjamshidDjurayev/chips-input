package co.djuraev.chipsinput;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import co.djuraev.chipsinput.chips.ChipItem;
import co.djuraev.chipsinput.chips.ChipsEditText;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  @BindView(R.id.tag_input) ChipsEditText tagsInput;

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
    chips.add("Chip2");
    chips.add("Chip3");
    chips.add("Chip4");
    chips.add("Chip5");

    TagHintsAdapter tagHintsAdapter
        = new TagHintsAdapter(this, suggestions);

    tagsInput.setThreshold(2);
    tagsInput.setAdapter(tagHintsAdapter);
    tagsInput.setChipsList(chips);
  }

  @OnClick(R.id.button_chips) protected void onChipsButtonClicked() {
    Toast.makeText(this, tagsInput.getAllChipsValue().toString(), Toast.LENGTH_LONG).show();
  }
}
