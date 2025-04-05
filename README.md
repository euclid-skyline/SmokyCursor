
 # Introduction:

The Application Idea is from Youtube. 
 This Application is created using Deepseek AI then handover to Gemini AI for analysis and documentation. The Application is Android App that implement live wallpaper simulates smoke effect.

# Classes

The important classes in App are as the following:

- SmokeWallpaperService Class
- Particle Class 

 ## SmokeWallpaperService Class:
 
 SmokeWallpaperService is a live wallpaper service that simulates a dynamic, interactive smoke effect.
 
 This service employs a particle system to generate a visually engaging smoke-like animation.
 User interactions through touch events significantly influence the smoke's behavior, creating an
 immersive and responsive experience. The smoke particles are governed by physics-based rules,
 exhibiting natural-looking movement, decay, and color transitions.
 
 ### Core Functionality:
 
 -   **Interactive Particle Generation:** Responds to user touches by spawning new smoke particles at the touch location.
 -   **Physics-Driven Simulation:** Simulates physical phenomena like air resistance, buoyancy (float force), and random noise to achieve realistic particle motion.
 -   **Dynamic Color Transition:** Smoothly transitions each particle's color from a vibrant orange to a deep blue hue over time.
 -   **Organic Decay and Fading:** Particles gradually shrink in size and fade out in opacity as they age, mimicking the dissipation of real smoke.
 -   **Optimized Rendering:** Employs coroutines for smooth, frame-by-frame animation updates and efficient rendering.
 * -   **Customizable Behavior:** Offers a wide array of tunable parameters, allowing for adjustments to particle count, physics properties, color schemes, and more.
 
 ### Implementation Details:
 
 -   **Wallpaper Engine:** Extends `WallpaperService` and implements a custom `SmokeEngine` as its rendering engine.
 -   **Coroutine-Based Animation:** `SmokeEngine` utilizes coroutines for asynchronous task management, particularly for the animation loop and fade-out sequences.
 -   **Particle System:** Manages a collection of `Particle` objects, each representing a single puff of smoke.
 -   **Touch Interaction:** Processes touch events (`ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`, `ACTION_CANCEL`) to dynamically control particle generation and behavior.
 -   **Canvas Drawing:** Leverages Android's `Canvas` API to draw particles onto the wallpaper surface.
 - **Physics simulation** Simulate `airResistance`, `noise`, `buoyancy` and `rotation`.
 - **Size and decay management** Manage the particle size and its decay rate.
 - **Fade out system** Allow to fade out particles using a coroutine.

### How it works:

[Under Construction]

## Particle Class:

Represents a single particle in a particle system.
 
 This class encapsulates the properties and behavior of a particle, including its
 position, size, movement, visual attributes, and special effects. It also manages
 the particle's lifecycle and provides methods for calculating its state over time.
 
### Class Properties
 -  *x* : The current horizontal position of the particle.
 -  *y* : The current vertical position of the particle.
 -  *radius* : The current radius of the particle. This value decreases over time based on `baseDecay`.
 -  *velocityX* : The horizontal velocity of the particle, determining its movement speed in the X direction.
 -  *velocityY* : The vertical velocity of the particle, determining its movement speed in the Y direction.
 -  *baseDecay* : The base rate at which the particle's radius decreases over time. A higher value means faster shrinking.
 -  *alpha* : The opacity of the particle, ranging from 0 (fully transparent) to 255 (fully opaque).
 -  *floatForce* : A force that influences the particle's vertical movement, typically used for a floating or rising effect. A positive value makes the particle move upward.
 -  *rotation* : The current rotation angle of the particle (in degrees).
 -  *rotationSpeed* : The rate at which the particle rotates per update, in degrees.
  
### Class Methods

[Under Construction]



