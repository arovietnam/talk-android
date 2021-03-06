/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.presenters;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;

import com.nextcloud.talk.adapters.items.MentionAutocompleteItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.models.json.mention.MentionOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.singletons.ApplicationWideApiHolder;
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class MentionAutocompletePresenter extends RecyclerViewPresenter<Mention> implements FlexibleAdapter.OnItemClickListener {
    private NcApi ncApi;
    private UserEntity currentUser;

    @Inject
    UserUtils userUtils;

    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private Context context;

    private String roomToken;

    private List<AbstractFlexibleItem> abstractFlexibleItemList = new ArrayList<>();

    public MentionAutocompletePresenter(Context context) {
        super(context);
        this.context = context;
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        setupNcApi();
    }

    public MentionAutocompletePresenter(Context context, String roomToken) {
        super(context);
        this.roomToken = roomToken;
        this.context = context;
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        setupNcApi();
    }

    private void setupNcApi() {
        currentUser = userUtils.getCurrentUser();
        ncApi = ApplicationWideApiHolder.getInstance().getNcApiInstanceForAccountId(currentUser.getId(), null);
    }

    @Override
    protected RecyclerView.Adapter instantiateAdapter() {
        adapter = new FlexibleAdapter<>(abstractFlexibleItemList, context, false);
        adapter.addListener(this);
        return adapter;
    }

    @Override
    protected void onQuery(@Nullable CharSequence query) {
        if (!TextUtils.isEmpty(query)) {

            adapter.setFilter(query.toString());
            ncApi.getMentionAutocompleteSuggestions(ApiUtils.getCredentials(currentUser.getUserId(), currentUser
                            .getToken()), ApiUtils.getUrlForMentionSuggestions(currentUser.getBaseUrl(), roomToken),
                    query.toString(), null)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3)
                    .subscribe(new Observer<MentionOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                        }

                        @Override
                        public void onNext(MentionOverall mentionOverall) {
                            List<Mention> mentionsList = mentionOverall.getOcs().getData();

                            if (mentionsList.size() == 0) {
                                adapter.clear();
                            } else {
                                List<AbstractFlexibleItem> internalAbstractFlexibleItemList = new ArrayList<>();
                                for (Mention mention : mentionsList) {
                                    internalAbstractFlexibleItemList.add(
                                            new MentionAutocompleteItem(mention.getId(), mention.getLabel(),
                                                    currentUser));
                                }

                                if (adapter.getItemCount() != 0) {
                                    adapter.clear();
                                }

                                adapter.updateDataSet(internalAbstractFlexibleItemList);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            adapter.clear();
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        } else {
            adapter.clear();
        }
    }

    @Override
    public boolean onItemClick(View view, int position) {
        Mention mention = new Mention();
        MentionAutocompleteItem mentionAutocompleteItem = (MentionAutocompleteItem) adapter.getItem(position);
        if (mentionAutocompleteItem != null) {
            mention.setId(mentionAutocompleteItem.getUserId());
            mention.setLabel(mentionAutocompleteItem.getDisplayName());
            mention.setSource("users");
            dispatchClick(mention);
        }
        return true;
    }
}
