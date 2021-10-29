package com.w3engineers.unicef.telemesh.ui.welcome;

import androidx.lifecycle.MutableLiveData;

import com.w3engineers.unicef.telemesh.data.local.db.DataSource;
import com.w3engineers.unicef.telemesh.data.local.dbsource.Source;
import com.w3engineers.unicef.util.base.ui.BaseRxViewModel;

import io.reactivex.schedulers.Schedulers;

public class WelcomeViewModel extends BaseRxViewModel {

    private DataSource dataSource;
    private MutableLiveData<Boolean> walletPrepareLiveData;

    public WelcomeViewModel() {
        dataSource = Source.getDbSource();
        walletPrepareLiveData = new MutableLiveData<>();
    }

    public void initWalletPreparationCallback() {
        getCompositeDisposable().add(dataSource.getWalletPrepared()
                .subscribeOn(Schedulers.newThread())
                .subscribe(aBoolean -> {
                    walletPrepareLiveData.postValue(aBoolean);
                }, Throwable::printStackTrace));
    }

    public MutableLiveData<Boolean> getWalletPrepareLiveData() {
        return walletPrepareLiveData;
    }
}
