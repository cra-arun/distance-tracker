// Java Code: Signup.java
package com.example.distance_track;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class signup extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button signupButton;
    private TextView loginRedirectText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        emailEditText = findViewById(R.id.signup_email);
        passwordEditText = findViewById(R.id.signup_password);
        signupButton = findViewById(R.id.signup_button);
        loginRedirectText = findViewById(R.id.loginRedirectText);

        mAuth = FirebaseAuth.getInstance();

        signupButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.sendEmailVerification().addOnCompleteListener(verifyTask -> {
                            if (verifyTask.isSuccessful()) {
                                Toast.makeText(this, "Verification email sent. Verify and login.", Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                                startActivity(new Intent(this, login.class));
                                finish();
                            } else {
                                Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    Toast.makeText(this, "Signup failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

        loginRedirectText.setOnClickListener(v -> {
            startActivity(new Intent(this, login.class));
            finish();
        });
    }
}
