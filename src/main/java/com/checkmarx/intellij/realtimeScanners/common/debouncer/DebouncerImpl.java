package com.checkmarx.intellij.realtimeScanners.common.debouncer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class DebouncerImpl  implements Debouncer, Disposable {

    private final Map<String, Alarm> pendingEventsMap=new ConcurrentHashMap<String,Alarm>();


    public DebouncerImpl(@NotNull Disposable parentDisposable) {
        Disposer.register(parentDisposable,this);
    }

    @Override
    public void debounce(@NotNull String uri,@NotNull Runnable task,int delay ){
        Alarm existing=pendingEventsMap.get(uri);
        if(existing!=null){
            cancel(uri);
        }
         Alarm alarm= new Alarm(Alarm.ThreadToUse.POOLED_THREAD,this);
         pendingEventsMap.put(uri,alarm);
         alarm.addRequest(()->{
             pendingEventsMap.remove(uri);
             task.run();

         },delay);
    }

    @Override
    public void cancel(@NotNull String key){
      Alarm existing= pendingEventsMap.get(key);
      if(existing!=null){
          existing.cancelAllRequests();
      }
    }

    @Override
    public void dispose(){
         pendingEventsMap.values().forEach(Alarm::cancelAllRequests);
         pendingEventsMap.clear();
    }

}
