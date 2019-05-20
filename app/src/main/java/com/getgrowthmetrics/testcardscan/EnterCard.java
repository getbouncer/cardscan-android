package com.getgrowthmetrics.testcardscan;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.stripe.android.view.CardInputWidget;
import com.stripe.android.view.CardMultilineWidget;

public class EnterCard extends AppCompatActivity {

    private CardInputWidget cardInputWidget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_card);

        String number = getIntent().getStringExtra("number");
        int expiryMonth = getIntent().getIntExtra("expiryMonth", -1);
        int expiryYear = getIntent().getIntExtra("expiryYear", -1);


        cardInputWidget = findViewById(R.id.card_input_widget);
        if (number != null) {
            cardInputWidget.setCardNumber(number);
        }

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
