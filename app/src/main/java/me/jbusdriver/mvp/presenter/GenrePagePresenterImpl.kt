package me.jbusdriver.mvp.presenter

import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.common.CacheLoader
import me.jbusdriver.common.KLog
import me.jbusdriver.common.SchedulersCompat
import me.jbusdriver.common.addUserCase
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.GenrePageContract
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.model.AbstractBaseModel
import me.jbusdriver.mvp.model.BaseModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class GenrePagePresenterImpl(val url: String) : BasePresenterImpl<GenrePageContract.GenrePageView>(), GenrePageContract.GenrePagePresenter {

    val model: BaseModel<String, Document> = object : AbstractBaseModel<String, Document>({ url ->
        JAVBusService.INSTANCE.get(url).addUserCase().map { Jsoup.parse(it) }
    }) {
        override fun requestFromCache(t: String) = Flowable.concat(CacheLoader.justLru(url).map { Jsoup.parse(it) }, requestFor(t)).firstOrError().toFlowable()
    }


    override fun onFirstLoad() {
        super.onFirstLoad()
        mView?.showLoading()
        model.requestFromCache(url)
                .map {
                    val generes = it.select(".genre-box")
                    val titles = generes.prev().map { it.text() }
                    val list = generes.map { it.select("a").map { Genre(it.text(), it.attr("href")) } }

                    mView?.let {
                        it.titleValues.clear()
                        it.fragmentValues.clear()
                        it.titleValues.addAll(titles)
                        it.fragmentValues.addAll(list)
                    }
                    Unit
                }.doAfterTerminate { AndroidSchedulers.mainThread().scheduleDirect { mView?.dismissLoading() } }
                .compose(SchedulersCompat.io())
                .subscribeBy(onError = {
                    KLog.d(it)
                }, onComplete = {
                    mView?.showContent(null)
                }).addTo(rxManager)
    }

    override fun lazyLoad() {
        onFirstLoad()
    }
}