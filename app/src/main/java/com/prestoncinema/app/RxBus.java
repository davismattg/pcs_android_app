package com.prestoncinema.app;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.jakewharton.rxrelay2.BehaviorRelay;

import java.lang.annotation.Retention;
import java.util.HashMap;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import rx.subscriptions.CompositeSubscription;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Created by MATT on 3/19/2018.
 */
public final class RxBus {
    private static SparseArray<PublishSubject<Object>> sSubjectMap = new SparseArray<>();
    private static Map<Object, CompositeSubscription> sSubscriptionsMap = new HashMap<>();

    public static final int SUBJECT_LENS_UPDATED = 0;
    public static final int SUBJECT_LENS_LIST_UPDATED = 1;

    @Retention(SOURCE)
    @IntDef({SUBJECT_LENS_UPDATED, SUBJECT_LENS_LIST_UPDATED})
    @interface
    Subject {

    }

    private RxBus() {
        // hidden constructor...
    }

    /**
     * Get the subject, or create it if it's not already in memory.
     */
    @NonNull
    private static PublishSubject<Object> getSubject(@Subject int subjectCode) {
        PublishSubject<Object> subject = sSubjectMap.get(subjectCode);
        if (subject == null) {      // create the subject
            subject = PublishSubject.create();
            subject.subscribeOn(AndroidSchedulers.mainThread());
            sSubjectMap.put(subjectCode, subject);
        }

        return subject;
    }

    /**
     * Get the CompositeSubscription, or create it if it's not already in memory
     */
    @NonNull
    private static CompositeSubscription getCompositeSubscription(@NonNull Object object) {
        CompositeSubscription cs = sSubscriptionsMap.get(object);
        if (cs == null) {
            cs = new CompositeSubscription();
            sSubscriptionsMap.put(object, cs);
        }

        return cs;
    }

    /**
     * Subscribe to the specified subject and listen for updates on that subject.
     * Pass in object to associate your registration with, so you can unsubscribe later.
     * @param subject
     * @param lifecycle
     * @param action
     */
    public static void subscribe(@Subject int subject, @NonNull Object lifecycle, @NonNull Action1<Object> action) {
        Subscription subscription = getSubject(subject).subscribe(action);
        getCompositeSubscription(lifecycle).add(subscription);
    }

    /**
     * Unregister the object from the bus, removing all subscriptions.
     * This should be called when the object it going to go out of memory
     * @param lifecycle
     */
    public static void unregister(@NonNull Object lifecycle) {
        // We have to remove the composition from the map, because once you unsubscribe it can't be used anymore
        CompositeSubscription cs = sSubscriptionsMap.remove(lifecycle);
        if (cs != null) {
            cs.unsubscribe();
        }
    }

    /**
     * Publish an object to the specified subject for all subscribers of that subject
     * @param subject
     * @param message
     */
    public static void publish(@Subject int subject, @NonNull Object message) {
        getSubject(subject).onNext(message);
    }

}

