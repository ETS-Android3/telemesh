package com.w3engineers.unicef.telemesh.ui.createuser;

import android.annotation.SuppressLint;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;

import com.jakewharton.rxbinding2.widget.RxTextView;
import com.w3engineers.ext.strom.application.ui.base.BaseActivity;
import com.w3engineers.ext.strom.util.helper.Toaster;
import com.w3engineers.unicef.telemesh.R;
import com.w3engineers.unicef.telemesh.data.helper.constants.Constants;
import com.w3engineers.unicef.telemesh.data.provider.ServiceLocator;
import com.w3engineers.unicef.telemesh.databinding.ActivityCreateUserBinding;
import com.w3engineers.unicef.telemesh.ui.chooseprofileimage.ProfileImageActivity;
import com.w3engineers.unicef.telemesh.ui.main.MainActivity;
import com.w3engineers.unicef.telemesh.ui.setorganization.SetOrganizationActivity;
import com.w3engineers.unicef.util.helper.uiutil.UIHelper;

import java.util.concurrent.TimeUnit;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.internal.util.AppendOnlyLinkedArrayList;
import io.reactivex.schedulers.Schedulers;

public class CreateUserActivity extends BaseActivity implements View.OnClickListener {

    private ActivityCreateUserBinding mBinding;

//    private ServiceLocator serviceLocator;
    private int PROFILE_IMAGE_REQUEST = 1;
    public static int INITIAL_IMAGE_INDEX = -1;
    private CreateUserViewModel mViewModel;
    @NonNull
    public static String IMAGE_POSITION = "image_position";


    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_user;
    }

    @Override
    protected int statusBarColor() {
        return R.color.colorPrimaryDark;
    }

    @SuppressLint("CheckResult")
    @Override
    protected void startUI() {

        mBinding = (ActivityCreateUserBinding) getViewDataBinding();
        setTitle(getString(R.string.create_user));
        mViewModel = getViewModel();

        UIHelper.hideKeyboardFrom(this, mBinding.editTextName);

        mBinding.imageViewCamera.setOnClickListener(this);
        mBinding.buttonSignup.setOnClickListener(this);
        mBinding.imageProfile.setOnClickListener(this);

        mBinding.editTextName.setMaxCharacters(Constants.DefaultValue.MAXIMUM_TEXT_LIMIT);
        mBinding.editTextName.setMinCharacters(Constants.DefaultValue.MINIMUM_TEXT_LIMIT);

        mViewModel.textChangeLiveData.observe(this, this::nextButtonControl);
        mViewModel.textEditControl(mBinding.editTextName);
    }

    private void nextButtonControl(String nameText) {
        if (mViewModel.getImageIndex() >= 0 &&
                !TextUtils.isEmpty(nameText) &&
                nameText.length() >= Constants.DefaultValue.MINIMUM_TEXT_LIMIT) {
            mBinding.buttonSignup.setBackgroundResource(R.drawable.ractangular_gradient);
            mBinding.buttonSignup.setTextColor(getResources().getColor(R.color.white));
            mBinding.buttonSignup.setClickable(true);
        } else {
            mBinding.buttonSignup.setBackgroundResource(R.drawable.ractangular_white);
            mBinding.buttonSignup.setTextColor(getResources().getColor(R.color.deep_grey));
            mBinding.buttonSignup.setClickable(false);
        }
    }

    private CreateUserViewModel getViewModel() {
        return ViewModelProviders.of(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) ServiceLocator.getInstance().getCreateUserViewModel(getApplication());
            }
        }).get(CreateUserViewModel.class);
    }

    @Override
    public void onClick(@NonNull View view) {
        super.onClick(view);

        int id = view.getId();

        switch (id) {
            case R.id.button_signup:
                saveData();
                break;
            case R.id.image_profile:
            case R.id.image_view_camera:
                Intent intent = new Intent(this, ProfileImageActivity.class);
                intent.putExtra(CreateUserActivity.IMAGE_POSITION, mViewModel.getImageIndex());
                startActivityForResult(intent, PROFILE_IMAGE_REQUEST);
                break;

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data != null && requestCode == PROFILE_IMAGE_REQUEST && resultCode == RESULT_OK) {
            mViewModel.setImageIndex(data.getIntExtra(IMAGE_POSITION, INITIAL_IMAGE_INDEX));

            int id = getResources().getIdentifier(Constants.drawables.AVATAR_IMAGE + mViewModel.getImageIndex(), Constants.drawables.AVATAR_DRAWABLE_DIRECTORY, getPackageName());
            mBinding.imageProfile.setImageResource(id);

            nextButtonControl(mBinding.editTextName.getText().toString());
        }
    }

    private void saveData() {
        if (mViewModel.getImageIndex() != INITIAL_IMAGE_INDEX) {
            if (mViewModel.storeData(mBinding.editTextName.getText() + "")) {
                Intent intent = new Intent(CreateUserActivity.this, SetOrganizationActivity.class);
                startActivity(intent);
            }
        } else {
            Toaster.showLong(getString(R.string.select_avatar));
        }
    }
}
