import SwiftUI

struct SVGImageView: View {
    let name: String
    let size: CGFloat

    var body: some View {
        Image(name)
            .resizable()
            .scaledToFit()
            .padding(size * 0.1)
            .frame(width: size, height: size)
    }
}
