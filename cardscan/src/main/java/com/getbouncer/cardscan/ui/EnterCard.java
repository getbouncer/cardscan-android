package com.getbouncer.cardscan.ui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.getbouncer.cardscan.R;
import com.stripe.android.view.CardMultilineWidget;

public class EnterCard extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_card);

        String number = getIntent().getStringExtra("number");
        int expiryMonth = getIntent().getIntExtra("expiryMonth", -1);
        int expiryYear = getIntent().getIntExtra("expiryYear", -1);

        CardMultilineWidget cardInputWidget = findViewById(R.id.card_input_widget);
        if (number != null) {
            cardInputWidget.setCardNumber(number);
            cardInputWidget.getChildAt(1).requestFocus();
        }
    }
}
