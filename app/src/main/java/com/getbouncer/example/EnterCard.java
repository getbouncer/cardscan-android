package com.getbouncer.example;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import com.getbouncer.cardscan.CreditCard;
import com.stripe.android.view.CardInputWidget;

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

        TextView textView = findViewById(R.id.cardNumberForTesting);
        textView.setText(number);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cardInputWidget.requestFocus();
    }
}
