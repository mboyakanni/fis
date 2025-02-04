package it.niedermann.fis.operation.remote.notification.sms.io.sms77;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

interface Sms77Service {
    @GET("sms?debug=1&return_msg_id=1")
    Call<ResponseBody> sendSms(
            @Query("p") String apiKey,
            @Query("to") String to,
            @Query("text") String text
    );
}
