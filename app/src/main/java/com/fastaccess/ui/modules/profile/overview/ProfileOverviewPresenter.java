package com.fastaccess.ui.modules.profile.overview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.fastaccess.data.dao.model.Login;
import com.fastaccess.data.dao.model.User;
import com.fastaccess.helper.BundleConstant;
import com.fastaccess.helper.InputHelper;
import com.fastaccess.helper.RxHelper;
import com.fastaccess.provider.rest.RestProvider;
import com.fastaccess.ui.base.mvp.presenter.BasePresenter;
import com.fastaccess.ui.widgets.contributions.ContributionsDay;
import com.fastaccess.ui.widgets.contributions.ContributionsProvider;
import com.fastaccess.ui.widgets.contributions.GitHubContributionsView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;

/**
 * Created by Kosh on 03 Dec 2016, 9:16 AM
 */

class ProfileOverviewPresenter extends BasePresenter<ProfileOverviewMvp.View> implements ProfileOverviewMvp.Presenter {
    @com.evernote.android.state.State boolean isSuccessResponse;
    @com.evernote.android.state.State boolean isFollowing;
    @com.evernote.android.state.State String login;
    @com.evernote.android.state.State ArrayList<User> userOrgs = new ArrayList<>();
    private ArrayList<ContributionsDay> contributions = new ArrayList<>();
    private static final String URL = "https://github.com/users/%s/contributions";

    @Override public void onCheckFollowStatus(@NonNull String login) {
        if (!TextUtils.equals(login, Login.getUser().getLogin())) {
            manageDisposable(RxHelper.getObserver(RestProvider.getUserService(isEnterprise()).getFollowStatus(login))
                    .subscribe(booleanResponse -> {
                        isSuccessResponse = true;
                        isFollowing = booleanResponse.code() == 204;
                        sendToView(ProfileOverviewMvp.View::invalidateFollowBtn);
                    }, Throwable::printStackTrace));
        }
    }

    @Override public boolean isSuccessResponse() {
        return isSuccessResponse;
    }

    @Override public boolean isFollowing() {
        return isFollowing;
    }

    @Override public void onFollowButtonClicked(@NonNull String login) {
        manageDisposable(RxHelper.getObserver(!isFollowing ? RestProvider.getUserService(isEnterprise()).followUser(login)
                                                           : RestProvider.getUserService(isEnterprise()).unfollowUser(login))
                .subscribe(booleanResponse -> {
                    if (booleanResponse.code() == 204) {
                        isFollowing = !isFollowing;
                        sendToView(ProfileOverviewMvp.View::invalidateFollowBtn);
                    }
                }, this::onError));
    }

    @Override public void onError(@NonNull Throwable throwable) {
        int statusCode = RestProvider.getErrorCode(throwable);
        if (statusCode == 404) {
            sendToView(ProfileOverviewMvp.View::onUserNotFound);
            return;
        }
        if (!InputHelper.isEmpty(login)) {
            onWorkOffline(login);
        }
        sendToView(ProfileOverviewMvp.View::invalidateFollowBtn);
        super.onError(throwable);
    }

    @Override public void onFragmentCreated(@Nullable Bundle bundle) {
        if (bundle == null || bundle.getString(BundleConstant.EXTRA) == null) {
            throw new NullPointerException("Either bundle or User is null");
        }
        login = bundle.getString(BundleConstant.EXTRA);
        if (login != null) {
            loadOrgs();
//            loadUrlBackgroundImage();
            makeRestCall(RestProvider.getUserService(isEnterprise()).getUser(login), userModel -> {
                onSendUserToView(userModel);
                if (userModel != null) {
                    userModel.save(userModel);
                    if (userModel.getType() != null && userModel.getType().equalsIgnoreCase("user")) {
                        onCheckFollowStatus(login);
                    }
                }
            });
        }
    }

    @Override public void onWorkOffline(@NonNull String login) {
        User userModel = User.getUser(login);
        if (userModel == null) {
            return;
        }
        onSendUserToView(userModel);
    }

    @Override public void onSendUserToView(@Nullable User userModel) {
        sendToView(view -> view.onInitViews(userModel));
    }

    @Override public void onLoadContributionWidget(@NonNull GitHubContributionsView gitHubContributionsView) {
        if (!isEnterprise()) {
            if (contributions == null || contributions.isEmpty()) {
                String url = String.format(URL, login);
                manageDisposable(RxHelper.getObserver(RestProvider.getUserService(false).getContributions(url))
                        .flatMap(s -> Observable.just(new ContributionsProvider().getContributions(s)))
                        .subscribe(lists -> {
                            contributions.clear();
                            contributions.addAll(lists);
                            loadContributions(contributions, gitHubContributionsView);
                        }, Throwable::printStackTrace));
            } else {
                loadContributions(contributions, gitHubContributionsView);
            }
        }
    }

    @NonNull @Override public ArrayList<User> getOrgs() {
        return userOrgs;
    }

    @NonNull @Override public ArrayList<ContributionsDay> getContributions() {
        return contributions;
    }

    @NonNull @Override public String getLogin() {
        return login;
    }

    private void loadContributions(ArrayList<ContributionsDay> contributions, GitHubContributionsView gitHubContributionsView) {
        List<ContributionsDay> filter = gitHubContributionsView.getLastContributions(contributions);
        if (filter != null && contributions != null) {
            Observable<Bitmap> bitmapObservable = Observable.just(gitHubContributionsView.drawOnCanvas(filter, contributions));
            manageObservable(bitmapObservable
                    .doOnNext(bitmap -> sendToView(view -> view.onInitContributions(bitmap != null))));
        }
    }

    private void loadOrgs() {
        boolean isMe = login.equalsIgnoreCase(Login.getUser() != null ? Login.getUser().getLogin() : "");
        manageDisposable(RxHelper.getObserver(isMe ? RestProvider.getOrgService(isEnterprise()).getMyOrganizations()
                                                   : RestProvider.getOrgService(isEnterprise()).getMyOrganizations(login))
                .subscribe(response -> {
                    if (response != null && response.getItems() != null) {
                        userOrgs.addAll(response.getItems());
                    }
                    sendToView(view -> view.onInitOrgs(userOrgs));
                }, Throwable::printStackTrace));
    }

}