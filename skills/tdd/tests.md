# Good and Bad Tests

## Good Tests

**Integration-style**: Test through real interfaces, not mocks of internal parts.

```java
// GOOD: Tests observable behavior
@Test
void userCanCheckoutWithValidCart() {
    Cart cart = createCart();
    cart.add(product);

    CheckoutResult result = checkout(cart, paymentMethod);

    assertThat(result.status()).isEqualTo(Status.CONFIRMED);
}
```

Characteristics:

- Tests behavior users/callers care about
- Uses public API only
- Survives internal refactors
- Describes WHAT, not HOW
- One logical assertion per test

## Bad Tests

**Implementation-detail tests**: Coupled to internal structure.

```java
// BAD: Tests implementation details
@Test
void checkoutCallsPaymentServiceProcess() {
    PaymentService paymentService = mock(PaymentService.class);
    Checkout checkout = new Checkout(paymentService);

    checkout.checkout(cart, payment);

    verify(paymentService).process(cart.total());
}
```

Red flags:

- Mocking internal collaborators
- Testing private methods
- Asserting on call counts/order
- Test breaks when refactoring without behavior change
- Test name describes HOW not WHAT
- Verifying through external means instead of interface

```java
// BAD: Bypasses interface to verify
@Test
void createUserSavesToDatabase() {
    userService.createUser(new NewUser("Alice"));

    Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT * FROM users WHERE name = ?", "Alice");

    assertThat(row).isNotNull();
}

// GOOD: Verifies through interface
@Test
void createUserMakesUserRetrievable() {
    User user = userService.createUser(new NewUser("Alice"));

    User retrieved = userService.getUser(user.id());

    assertThat(retrieved.name()).isEqualTo("Alice");
}
```

**Tautological tests**: Expected value restates the implementation, so the test passes by construction.

```java
// BAD: Expected value is recomputed the way the code computes it
@Test
void calculateTotalSumsLineItems() {
    List<LineItem> items = List.of(new LineItem(10), new LineItem(5));
    int expected = items.stream().mapToInt(LineItem::price).sum();

    assertThat(calculateTotal(items)).isEqualTo(expected);
}

// GOOD: Expected value is an independent, known literal
@Test
void calculateTotalSumsLineItems() {
    List<LineItem> items = List.of(new LineItem(10), new LineItem(5));

    assertThat(calculateTotal(items)).isEqualTo(15);
}
```