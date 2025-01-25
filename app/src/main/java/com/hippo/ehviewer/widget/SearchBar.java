/*
 * Copyright 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.widget;

import static com.hippo.ehviewer.client.EhTagDatabase.NAMESPACE_TO_PREFIX;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.view.ViewTransition;
import com.hippo.lib.yorozuya.AnimationUtils;
import com.hippo.lib.yorozuya.MathUtils;
import com.hippo.lib.yorozuya.SimpleAnimatorListener;
import com.hippo.lib.yorozuya.ViewUtils;

import java.util.ArrayList;
import java.util.List;

public class SearchBar extends CardView implements View.OnClickListener,
        TextView.OnEditorActionListener, TextWatcher,
        SearchEditText.SearchEditTextListener {

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_STATE = "state";

    private static final long ANIMATE_TIME = 300L;

    public static final int STATE_NORMAL = 0;
    public static final int STATE_SEARCH = 1;
    public static final int STATE_SEARCH_LIST = 2;

    private int mState = STATE_NORMAL;

    private final Rect mRect = new Rect();
    private int mWidth;
    private int mHeight;
    private int mBaseHeight;
    private float mProgress;

    private ImageView mMenuButton;
    private TextView mTitleTextView;
    private ImageView mActionButton;
    private SearchEditText mEditText;
    private ListView mListView;
    private View mListContainer;
    private View mListHeader;

    private ViewTransition mViewTransition;

    private SearchDatabase mSearchDatabase;
    private List<Suggestion> mSuggestionList;
    private SuggestionAdapter mSuggestionAdapter;

    private Helper mHelper;
    private OnStateChangeListener mOnStateChangeListener;
    private SuggestionProvider mSuggestionProvider;

    private boolean mAllowEmptySearch = true;

    private boolean mInAnimation;

    private boolean showTranslation;

    private boolean isComeFromDownload = false;

    public SearchBar(Context context) {
        super(context);
        init(context);
    }

    public SearchBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SearchBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        showTranslation = Settings.getShowTagTranslations();
        mSearchDatabase = SearchDatabase.getInstance(getContext());

        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.widget_search_bar, this);
        mMenuButton = (ImageView) ViewUtils.$$(this, R.id.search_menu);
        mTitleTextView = (TextView) ViewUtils.$$(this, R.id.search_title);
        mActionButton = (ImageView) ViewUtils.$$(this, R.id.search_action);
        mEditText = (SearchEditText) ViewUtils.$$(this, R.id.search_edit_text);
        mListContainer = ViewUtils.$$(this, R.id.list_container);
        mListView = (ListView) ViewUtils.$$(mListContainer, R.id.search_bar_list);
        mListHeader = ViewUtils.$$(mListContainer, R.id.list_header);

        mViewTransition = new ViewTransition(mTitleTextView, mEditText);

        mTitleTextView.setOnClickListener(this);
        mMenuButton.setOnClickListener(this);
        mActionButton.setOnClickListener(this);
        mEditText.setSearchEditTextListener(this);
        mEditText.setOnEditorActionListener(this);
        mEditText.addTextChangedListener(this);

        // Get base height
        ViewUtils.measureView(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mBaseHeight = getMeasuredHeight();

        mSuggestionList = new ArrayList<>();
        mSuggestionAdapter = new SuggestionAdapter(LayoutInflater.from(getContext()));
        mListView.setAdapter(mSuggestionAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) -> mSuggestionList.get(position).onClick());
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            mSuggestionList.get(position).onLongClick();
            return true;
        });
    }

    private void addListHeader() {
        mListHeader.setVisibility(VISIBLE);
    }

    private void removeListHeader() {
        mListHeader.setVisibility(GONE);
    }

    private void updateSuggestions() {
        updateSuggestions(true);
    }

    private void updateSuggestions(boolean scrollToTop) {
        mSuggestionList.clear();
        Editable editable = mEditText.getText();
        String text = "";
        if (editable != null) {
            text = editable.toString();
        }


        if (mSuggestionProvider != null) {
            List<Suggestion> suggestions = mSuggestionProvider.providerSuggestions(text);
            if (suggestions != null && !suggestions.isEmpty()) {
                mSuggestionList.addAll(suggestions);
            }
        }

        String[] keywords = mSearchDatabase.getSuggestions(text, 128);
        for (String keyword : keywords) {
            mSuggestionList.add(new TagSuggestion(null, keyword));
        }

        EhTagDatabase ehTagDatabase = EhTagDatabase.getInstance(getContext());
        if (!TextUtils.isEmpty(text) && ehTagDatabase != null) {
            String[] s = text.split(" ");
            if (s.length > 0) {
                // String keyword = s[s.length - 1];
                String keyword = "";
                for (int i = s.length - 1; i >= 0; i--) {
                    if (s[i].contains(":") || s[i].contains("$")) {
                        break;
                    } else {
                        if(keyword.isEmpty())
                            keyword = s[i];
                        else
                            keyword = s[i] + " " + keyword;
                    }
                }
                keyword = keyword.trim();

                if(!keyword.isEmpty()) 
                {
                    List<Pair<String, String>> searchHints = ehTagDatabase.suggest(keyword);

                    for (Pair<String, String> searchHint : searchHints) {
                        if (showTranslation) {
                            mSuggestionList.add(new TagSuggestion(searchHint.first, searchHint.second));
                        } else {
                            mSuggestionList.add(new TagSuggestion(null, searchHint.second));
                        }
                    }
                }
            }
        }

        if (mSuggestionList.size() == 0) {
            removeListHeader();
        } else {
            addListHeader();
        }
        mSuggestionAdapter.notifyDataSetChanged();

        if (scrollToTop) {
            mListView.setSelection(0);
        }
    }

    public void setAllowEmptySearch(boolean allowEmptySearch) {
        mAllowEmptySearch = allowEmptySearch;
    }

    public float getEditTextTextSize() {
        return mEditText.getTextSize();
    }

    public void setEditTextHint(CharSequence hint) {
        mEditText.setHint(hint);
    }

    public void setEditTextHint(int resId) {
        mEditText.setHint(resId);
    }

    public void setHelper(Helper helper) {
        mHelper = helper;
    }

    public void setOnStateChangeListener(OnStateChangeListener listener) {
        mOnStateChangeListener = listener;
    }

    public void setSuggestionProvider(SuggestionProvider suggestionProvider) {
        mSuggestionProvider = suggestionProvider;
    }

    public void setText(String text) {
        mEditText.setText(text);
    }

    public String getText() {
        Editable text = mEditText.getText();
        if (text != null) {
            return text.toString();
        }
        return null;
    }

    public void cursorToEnd() {
        Editable text = mEditText.getText();
        if (text != null) {
            mEditText.setSelection(mEditText.getText().length());
        }
    }

    public void setTitle(String title) {
        mTitleTextView.setText(title);
    }

    public void setTitle(int resId) {
        mTitleTextView.setText(resId);
    }

    public void setSearch(String search) {
        mTitleTextView.setText(search);
        mEditText.setText(search);
    }

    public void setLeftDrawable(Drawable drawable) {
        if (drawable == null) {
            mMenuButton.setVisibility(View.GONE);
        }
        mMenuButton.setImageDrawable(drawable);
    }

    public void setLeftDrawable(ImageView view) {
        if (view == null) {
            mMenuButton.setVisibility(View.GONE);
        }
        mMenuButton = view;
    }

    public void setRightDrawable(Drawable drawable) {
        mActionButton.setImageDrawable(drawable);
    }

    public void setLeftIconVisibility(int visibility) {
        mMenuButton.setVisibility(visibility);
    }

    public void setRightIconVisibility(int visibility) {
        mActionButton.setVisibility(visibility);
    }

    public void setEditTextMargin(int left, int right) {
        MarginLayoutParams lp = (MarginLayoutParams) mEditText.getLayoutParams();
        lp.leftMargin = left;
        lp.rightMargin = right;
        mEditText.setLayoutParams(lp);
    }

    public void setIsComeFromDownload(boolean isComeFromDownload){
        this.isComeFromDownload = isComeFromDownload;
    }

    private void applySearch() {
        String query = mEditText.getText().toString().trim();

        if (!mAllowEmptySearch && TextUtils.isEmpty(query)) {
            return;
        }

        // Put it into db
        mSearchDatabase.addQuery(query);
        // Callback
        mHelper.onApplySearch(query);
    }

    public void applySearch(boolean hideKeyboard) {
        if (hideKeyboard) {
            hideKeyBoard();
        }
        applySearch();
    }

    public void hideKeyBoard() {
        InputMethodManager manager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void onClick(View v) {
        if (v == mTitleTextView) {
            mHelper.onClickTitle();
        } else if (v == mMenuButton) {
            mHelper.onClickLeftIcon();
        } else if (v == mActionButton) {
            mHelper.onClickRightIcon();
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (v == mEditText) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                applySearch();
                return true;
            }
        }
        return false;
    }


    public int getState() {
        return mState;
    }

    public void setState(int state) {
        setState(state, true);
    }

    public void setState(int state, boolean animation) {
        if (mState != state) {
            int oldState = mState;
            mState = state;

            switch (oldState) {
                default:
                case STATE_NORMAL:
                    mViewTransition.showView(1, animation);
                    mEditText.requestFocus();

                    if (state == STATE_SEARCH_LIST) {
                        showImeAndSuggestionsList(animation);
                    }
                    if (mOnStateChangeListener != null) {
                        mOnStateChangeListener.onStateChange(this, state, oldState, animation);
                    }
                    break;
                case STATE_SEARCH:
                    if (state == STATE_NORMAL) {
                        mViewTransition.showView(0, animation);
                    } else if (state == STATE_SEARCH_LIST) {
                        showImeAndSuggestionsList(animation);
                    }
                    if (mOnStateChangeListener != null) {
                        mOnStateChangeListener.onStateChange(this, state, oldState, animation);
                    }
                    break;
                case STATE_SEARCH_LIST:
                    hideImeAndSuggestionsList(animation);
                    if (state == STATE_NORMAL) {
                        mViewTransition.showView(0, animation);
                    }
                    if (mOnStateChangeListener != null) {
                        mOnStateChangeListener.onStateChange(this, state, oldState, animation);
                    }
                    break;
            }
        }
    }

    public void showImeAndSuggestionsList() {
        showImeAndSuggestionsList(true);
    }


    public void showImeAndSuggestionsList(boolean animation) {
        // Show ime
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mEditText, 0);
        // update suggestion for show suggestions list
        updateSuggestions();
        // Show suggestions list
        if (animation) {
            ObjectAnimator oa = ObjectAnimator.ofFloat(this, "progress", 1f);
            oa.setDuration(ANIMATE_TIME);
            oa.setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
            oa.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mListContainer.setVisibility(View.VISIBLE);
                    mInAnimation = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mInAnimation = false;
                }
            });
            oa.setAutoCancel(true);
            oa.start();
        } else {
            mListContainer.setVisibility(View.VISIBLE);
            setProgress(1f);
        }
    }

    private void hideImeAndSuggestionsList() {
        hideImeAndSuggestionsList(true);
    }

    private void hideImeAndSuggestionsList(boolean animation) {
        // Hide ime
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
        // Hide suggestions list
        if (animation) {
            ObjectAnimator oa = ObjectAnimator.ofFloat(this, "progress", 0f);
            oa.setDuration(ANIMATE_TIME);
            oa.setInterpolator(AnimationUtils.SLOW_FAST_INTERPOLATOR);
            oa.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mInAnimation = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mListContainer.setVisibility(View.GONE);
                    mInAnimation = false;
                }
            });
            oa.setAutoCancel(true);
            oa.start();
        } else {
            setProgress(0f);
            mListContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mListContainer.getVisibility() == View.VISIBLE) {
            mWidth = right - left;
            mHeight = bottom - top;
        }
    }

    @SuppressWarnings("unused")
    public void setProgress(float progress) {
        mProgress = progress;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getProgress() {
        return mProgress;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mInAnimation) {
            final int state = canvas.save();
            int bottom = MathUtils.lerp(mBaseHeight, mHeight, mProgress);
            mRect.set(0, 0, mWidth, bottom);
            canvas.clipRect(mRect);
            super.draw(canvas);
            canvas.restoreToCount(state);
        } else {
            super.draw(canvas);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Empty
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Empty
    }

    @Override
    public void afterTextChanged(Editable s) {
        updateSuggestions();
    }

    @Override
    public void onClick() {
        mHelper.onSearchEditTextClick();
    }

    @Override
    public void onBackPressed() {
        mHelper.onSearchEditTextBackPressed();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putInt(STATE_KEY_STATE, mState);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setState(savedState.getInt(STATE_KEY_STATE), false);
        }
    }

    public interface Helper {
        void onClickTitle();

        void onClickLeftIcon();

        void onClickRightIcon();

        void onSearchEditTextClick();

        void onApplySearch(String query);

        void onSearchEditTextBackPressed();
    }

    public interface OnStateChangeListener {

        void onStateChange(SearchBar searchBar, int newState, int oldState, boolean animation);
    }

    public interface SuggestionProvider {

        List<Suggestion> providerSuggestions(String text);
    }

    public abstract static class Suggestion {

        public abstract CharSequence getText(float textSize);

        public abstract CharSequence getText(TextView textView);

        public abstract void onClick();

        public abstract void onLongClick();
    }

    private class SuggestionAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        public SuggestionAdapter(LayoutInflater inflater) {
            this.inflater = inflater;
        }

        @Override
        public int getCount() {
            return mSuggestionList.size();
        }

        @Override
        public Object getItem(int position) {
            return mSuggestionList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout linearLayout;
            if (convertView == null) {
                linearLayout = (LinearLayout) inflater.inflate(R.layout.search_suggestion_item, parent, false);
            } else {
//                return convertView;
                linearLayout = (LinearLayout) convertView;
            }
            TextView hintView = linearLayout.findViewById(R.id.hintView);
            TextView textView = linearLayout.findViewById(R.id.textView);

            Suggestion suggestion = mSuggestionList.get(position);

            String hint = (String) suggestion.getText(hintView);
            String text = (String) suggestion.getText(textView);

            hintView.setText(hint);

            if (text == null || text.isEmpty()) {
                textView.setVisibility(GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                textView.setText(text);
            }

            return linearLayout;
        }
    }

    private class TagSuggestion extends Suggestion {
        public String show, mKeyword;

        public TagSuggestion(String show, String mKeyword) {
            this.show = show;
            this.mKeyword = mKeyword;
        }

        @Override
        public CharSequence getText(float textSize) {
            return null;
        }

        @Override
        public CharSequence getText(TextView textView) {
            if (textView.getId() == R.id.hintView) {
                return mKeyword;
            }
            return show;
        }

        /**
         * 无法替换中文
         * @param text1
         * @param text2
         * @return
         */
        public String removeCommonSubstring(String text1, String text2) {
            int m = text1.length();
            int n = text2.length();
            String match = "";

            for (int i = m - 1; i >= 0; i--) {
                String tmp = text1.substring(i, m);
                if (!text2.contains(tmp)) {
                    break;
                } else {
                    match = tmp;
                }
            }

            String result = text1.substring(0, m - match.length());
            return result;
        }

        public String replaceCommonSubstring(String tagKey, Editable editable) {
            String key = tagKey;
            if (editable.toString().contains(" ")) {
                StringBuilder builder = new StringBuilder(editable);
                char c = ' ';
                while (builder.charAt(builder.length() - 1) != c) {
                    builder.deleteCharAt(builder.length() - 1);
                }

                while (builder.length() != 0 && builder.charAt(builder.length() - 1) == c) {
                    builder.deleteCharAt(builder.length() - 1);
                }

                builder.append("  ").append(tagKey);
                key = builder.toString();
            }
            return key;
        }

        @Override
        public void onClick() {
            Editable editable = mEditText.getText();
            if (editable != null) {
                String tagKey = rebuildKeyword(mKeyword);
//                String newText = removeCommonSubstring(editable.toString(), mKeyword)+" "+tagKey;
                String newText = replaceCommonSubstring(tagKey,editable);
                mEditText.setText(newText);
                mEditText.setSelection(mEditText.getText().length());
            }
        }

        private String rebuildKeyword(String key) {
            String[] strings = key.split(":");
            if (strings.length != 2) {
                return key;
            }
            String groupName;
            String tagName = strings[1];
            if (isComeFromDownload){
                groupName = strings[0];
                return groupName+":"+tagName;
            }
            if (NAMESPACE_TO_PREFIX.containsKey(strings[0])) {
                groupName = NAMESPACE_TO_PREFIX.get(strings[0]);
                return groupName + "\"" + tagName + "$\"";
            } else {
                return key;
            }
        }



        @Override
        public void onLongClick() {
            mSearchDatabase.deleteQuery(mKeyword);
            updateSuggestions(false);
        }
    }

}
