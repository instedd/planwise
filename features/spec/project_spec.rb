describe "Project" do
  before(:each) {
    sign_in
  }

  it "should create a project" do
    expect_page HomePage do |page|  
      page.press_primary_button 
      #fill_in  "goal", :with => "Foo"
      page.find(".rc-dropdown b").click
      page.find(".chosen-drop li").click
    end 
  end

  it "should search project" do
  end

  it "should delete a project" do
  end

  it "should verify that only allowed users can access a project" do
  end

  it "should filter facilities by type" do
  end

  it "should set transport means options" do
  end
  
end