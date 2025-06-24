package com.example.distance_track;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

public class login extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private TextView signUpRedirectText, forgotPasswordText;
    private FirebaseAuth mAuth;

    private SignInButton googleSignInButton;
    private GoogleSignInClient mGoogleSignInClient;

    // Google Sign-In Launcher
    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        firebaseAuthWithGoogle(account.getIdToken());
                    } catch (ApiException e) {
                        Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            startActivity(new Intent(login.this, MainActivity.class)); // or HomeActivity
            finish(); // Don't show login screen again
            return;
        }

        setContentView(R.layout.activity_login);

        emailEditText = findViewById(R.id.login_email);
        passwordEditText = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        signUpRedirectText = findViewById(R.id.signUpRedirectText);
        forgotPasswordText = findViewById(R.id.forgot_password);
        googleSignInButton = findViewById(R.id.google_signin_button);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // set in strings.xml
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInButton.setOnClickListener(v -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailEditText.setError("Enter a valid email");
                return;
            }

            if (password.isEmpty() || password.length() < 6) {
                passwordEditText.setError("Password must be at least 6 characters");
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null && user.isEmailVerified()) {
                        startActivity(new Intent(login.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_SHORT).show();
                        mAuth.signOut();
                    }
                } else {
                    Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

        signUpRedirectText.setOnClickListener(v -> {
            startActivity(new Intent(this, signup.class));
            finish();
        });

        forgotPasswordText.setOnClickListener(v -> {
            Toast.makeText(this, "Password reset coming soon.", Toast.LENGTH_SHORT).show();
        });
    }


    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                startActivity(new Intent(login.this,  MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Google Sign-In failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
