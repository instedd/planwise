describe Home do
  it "should go to guisso for login" do
    goto_page Home do
      expect_page GuissoLogin
    end
  end
end
