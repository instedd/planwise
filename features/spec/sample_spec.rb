describe "Infraestructre" do
  it "should go to guisso" do
    visit 'http://guisso-local.instedd.org'
    screenshot_and_save_page
  end

  it "should go to resmap" do
    visit 'http://resmap-local.instedd.org'
    screenshot_and_save_page
  end

  it "should go to planwise" do
    visit '/'
    screenshot_and_save_page
  end

  it "should have no users" do
    expect(User.count).to eq 0
  end
end
