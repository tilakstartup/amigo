import SwiftUI

struct SignUpView: View {
    @ObservedObject var viewModel: AuthViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            ZStack {
                Color(.systemBackground)
                    .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 24) {
                        // Logo and title
                        VStack(spacing: 12) {
                            Image(systemName: "heart.fill")
                                .font(.system(size: 60))
                                .foregroundColor(.pink)
                            
                            // Show success screen if email confirmation is required
                            if let successMessage = viewModel.successMessage {
                                Text("Check Your Email")
                                    .font(.title)
                                    .fontWeight(.bold)
                                
                                Spacer().frame(height: 20)
                                
                                Image(systemName: "envelope.circle.fill")
                                    .font(.system(size: 80))
                                    .foregroundColor(.green)
                                
                                Spacer().frame(height: 20)
                                
                                Text(successMessage)
                                    .font(.body)
                                    .foregroundColor(.primary)
                                    .multilineTextAlignment(.center)
                                    .padding()
                                    .background(Color.green.opacity(0.1))
                                    .cornerRadius(10)
                                    .padding(.horizontal)
                                
                                Spacer().frame(height: 20)
                                
                                Button(action: {
                                    dismiss()
                                }) {
                                    Text("Back to Login")
                                        .fontWeight(.semibold)
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                }
                                .background(Color.pink)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                                .padding(.horizontal)
                            } else {
                                // Show signup form
                                Text("Create Account")
                                    .font(.title)
                                    .fontWeight(.bold)
                                
                                Text("Join Amigo today")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.top, 40)
                        .padding(.bottom, 20)
                        
                        // Only show form if no success message
                        if viewModel.successMessage == nil {
                        
                        // Email and password fields
                        VStack(spacing: 16) {
                            TextField("Email", text: $viewModel.email)
                                .textContentType(.emailAddress)
                                .autocapitalization(.none)
                                .keyboardType(.emailAddress)
                                .padding()
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                            
                            SecureField("Password", text: $viewModel.password)
                                .textContentType(.newPassword)
                                .padding()
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                            
                            SecureField("Confirm Password", text: $viewModel.confirmPassword)
                                .textContentType(.newPassword)
                                .padding()
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                        }
                        .padding(.horizontal)
                        
                        // Password requirements
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Password must:")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text("• Be at least 8 characters long")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal)
                        
                        // Error message
                        if let errorMessage = viewModel.errorMessage {
                            Text(errorMessage)
                                .font(.caption)
                                .foregroundColor(.red)
                                .padding(.horizontal)
                                .multilineTextAlignment(.center)
                        }
                        
                        // Sign up button
                        Button(action: {
                            Task {
                                await viewModel.signUp()
                                if viewModel.isAuthenticated {
                                    dismiss()
                                }
                            }
                        }) {
                            if viewModel.isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .frame(maxWidth: .infinity)
                                    .padding()
                            } else {
                                Text("Sign Up")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding()
                            }
                        }
                        .background(Color.pink)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                        .padding(.horizontal)
                        .disabled(viewModel.isLoading)
                        
                        // Divider
                        HStack {
                            Rectangle()
                                .frame(height: 1)
                                .foregroundColor(.gray.opacity(0.3))
                            Text("or")
                                .foregroundColor(.secondary)
                                .padding(.horizontal, 8)
                            Rectangle()
                                .frame(height: 1)
                                .foregroundColor(.gray.opacity(0.3))
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                        
                        // OAuth buttons
                        VStack(spacing: 12) {
                            Button(action: {
                                Task {
                                    await viewModel.signInWithGoogle()
                                    if viewModel.isAuthenticated {
                                        dismiss()
                                    }
                                }
                            }) {
                                HStack {
                                    Image(systemName: "g.circle.fill")
                                        .font(.title3)
                                    Text("Continue with Google")
                                        .fontWeight(.medium)
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                            }
                            .foregroundColor(.primary)
                            .disabled(viewModel.isLoading)
                            
                            Button(action: {
                                Task {
                                    await viewModel.signInWithApple()
                                    if viewModel.isAuthenticated {
                                        dismiss()
                                    }
                                }
                            }) {
                                HStack {
                                    Image(systemName: "apple.logo")
                                        .font(.title3)
                                    Text("Continue with Apple")
                                        .fontWeight(.medium)
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color(.secondarySystemBackground))
                                .cornerRadius(10)
                            }
                            .foregroundColor(.primary)
                            .disabled(viewModel.isLoading)
                        }
                        .padding(.horizontal)
                        }
                        
                        Spacer()
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}
