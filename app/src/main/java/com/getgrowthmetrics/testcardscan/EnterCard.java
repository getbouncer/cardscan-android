package com.getgrowthmetrics.testcardscan;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;

import com.getbouncer.cardscan.CreditCard;
import com.stripe.android.view.CardInputWidget;
import com.stripe.android.view.CardMultilineWidget;

import org.w3c.dom.Text;

public class EnterCard extends AppCompatActivity {

    private CardInputWidget cardInputWidget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_card);

        CreditCard card = getIntent().getParcelableExtra("card");
        String number = card.number;
        int expiryMonth = -1;
        if (!TextUtils.isEmpty(card.expiryMonth)) {
            expiryMonth = Integer.parseInt(card.expiryMonth);
        }
        int expiryYear = -1;
        if (!TextUtils.isEmpty(card.expiryYear)) {
            expiryYear = Integer.parseInt(card.expiryYear);
        }


        cardInputWidget = findViewById(R.id.card_input_widget);
        cardInputWidget.setCardNumber(number);

        if (expiryMonth > 0 && expiryYear > 0) {
            cardInputWidget.setExpiryDate(expiryMonth, expiryYear);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cardInputWidget.requestFocus();
    }
}
