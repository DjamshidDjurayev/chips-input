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

import android.text.TextUtils;

class SimpleRecipientChip implements BaseRecipientChip {
	private final CharSequence mDisplay;

	private final CharSequence mValue;


	private final ChipItem mEntry;

	private boolean mSelected = false;

	private CharSequence mOriginalText;

	SimpleRecipientChip(final ChipItem entry) {
		mDisplay = entry.getTitle();
		mValue = entry.getTitle().trim();
		mEntry = entry;
	}

	@Override
	public void setSelected(final boolean selected) {
		mSelected = selected;
	}

	@Override
	public boolean isSelected() {
		return mSelected;
	}

	@Override
	public CharSequence getDisplay() {
		return mDisplay;
	}

	@Override
	public CharSequence getValue() {
		return mValue;
	}

	@Override
	public ChipItem getEntry() {
		return mEntry;
	}

	@Override
	public void setOriginalText(final String text) {
		if (TextUtils.isEmpty(text)) {
			mOriginalText = text;
		} else {
			mOriginalText = text.trim();
		}
	}

	@Override
	public CharSequence getOriginalText() {
		return !TextUtils.isEmpty(mOriginalText) ? mOriginalText : mEntry.getTitle();
	}

	@Override
	public String toString() {
		return mDisplay + " <" + mValue + ">";
	}
}