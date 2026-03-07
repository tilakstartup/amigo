import SwiftUI
import shared

struct ContentView: View {
    let greeting = Greeting().greet()
    
    var body: some View {
        VStack {
            Image(systemName: "heart.fill")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text(greeting)
                .padding()
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
