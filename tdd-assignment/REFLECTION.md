# AI Productivity vs. Human Quality Guardrails

## Reflection

AI significantly accelerated the development of the `SubscriptionPricingService`, particularly during test creation and implementation. In the initial prompt, I provided the complete business requirements from the problem statement and asked AI to generate unit tests. AI was able to create tests covering the major requirements, including subscription tiers, longevity discounts, promotional vouchers, invalid vouchers, rounding, and the minimum `$0.00` floor. This provided a strong starting point for the TDD process and reduced the time required to manually create the initial test suite.

During the test audit phase, I reviewed the generated tests against the original requirements and identified a few areas that needed small adjustments. This review was important because the generated tests needed to be validated against the exact business rules rather than being accepted blindly.

Once the tests were in place, I used a concise implementation prompt asking AI to implement the required functionality. AI was able to implement the feature correctly and bring the complete test suite to green. This demonstrated how clearly defined requirements and automated tests can make AI-assisted development significantly faster.

However, AI still required human quality guardrails. The generated code and tests needed to be reviewed to ensure that the business rules were interpreted correctly and that no assumptions were introduced. In particular, boundary conditions, discount ordering, voucher behavior, and currency rounding required careful verification against the original specification.

TDD also helped prevent unnecessary technical debt during AI-assisted development. Instead of allowing AI to independently expand or modify the feature, the tests acted as a clear boundary for the expected behavior. AI had a concrete target to implement, while the tests provided an objective way to verify whether the implementation satisfied the requirements.

Overall, AI was most effective as a development accelerator rather than a replacement for engineering judgment. TDD provided the guardrail that kept the implementation focused on the defined requirements. The combination of AI-generated tests, human review, and test-driven implementation resulted in faster development while maintaining control over code quality and business correctness.