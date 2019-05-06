package com.getbouncer.cardscan;

import org.json.JSONException;
import org.json.JSONObject;

public class CreditCard {
    public String number;
    public String expiryMonth;
    public String expiryYear;

    public CreditCard(String jsonString) {
        try {
            JSONObject card = new JSONObject(jsonString);
            this.number = card.getString("number");
            if (card.has("expiryMonth")) {
                this.expiryMonth = card.getInt("expiryMonth") + "";
                this.expiryYear = card.getInt("expiryYear") + "";
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
